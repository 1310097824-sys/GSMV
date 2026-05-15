package com.gsmv.ai.report.export;

import com.gsmv.ai.report.dto.AiReportDtos;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

public final class AiReportPdfExporter {

    private static final float PAGE_MARGIN = 36F;
    private static final float TITLE_SIZE = 18F;
    private static final float SECTION_SIZE = 13F;
    private static final float TEXT_SIZE = 10.5F;

    private AiReportPdfExporter() {
    }

    public static byte[] export(AiReportDtos.AiReportDetailView report) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDFont font = loadFont(document);
            PdfWriter writer = new PdfWriter(document, font);
            writer.title(report.title());
            writer.line("报告类型: " + report.reportType() + "    时间范围: 近 " + report.days() + " 天");
            writer.line("生成时间: " + report.createdAt() + "    生成人: " + nullSafe(report.creatorName()));
            writer.blank();
            writer.section("摘要");
            writer.line(report.summary());
            writer.list("重点发现", report.highlights());
            writer.list("风险提示", report.risks());
            writer.list("建议行动", report.recommendations());
            writer.list("数据依据", report.evidence());
            writer.close();
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("导出 AI 报告 PDF 失败", exception);
        }
    }

    private static PDFont loadFont(PDDocument document) throws IOException {
        List<Path> candidates = List.of(
                Path.of("C:/Windows/Fonts/simhei.ttf"),
                Path.of("C:/Windows/Fonts/msyh.ttc"),
                Path.of("C:/Windows/Fonts/simsun.ttc")
        );
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return PDType0Font.load(document, candidate.toFile());
            }
        }
        return PDType1Font.HELVETICA;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static final class PdfWriter {
        private final PDDocument document;
        private final PDFont font;
        private PDPageContentStream contentStream;
        private PDPage page;
        private float y;

        private PdfWriter(PDDocument document, PDFont font) throws IOException {
            this.document = document;
            this.font = font;
            startPage();
        }

        private void title(String text) throws IOException {
            write(text, TITLE_SIZE, 8F);
        }

        private void section(String text) throws IOException {
            blank();
            write(text, SECTION_SIZE, 6F);
        }

        private void list(String title, List<String> values) throws IOException {
            section(title);
            if (values == null || values.isEmpty()) {
                line("暂无");
                return;
            }
            for (String value : values) {
                line("- " + value);
            }
        }

        private void line(String text) throws IOException {
            write(text, TEXT_SIZE, 4F);
        }

        private void blank() throws IOException {
            ensure(12F);
            y -= 12F;
        }

        private void write(String text, float size, float spacing) throws IOException {
            for (String line : wrap(sanitize(nullSafe(text)), size)) {
                ensure(size + spacing);
                contentStream.beginText();
                contentStream.setFont(font, size);
                contentStream.newLineAtOffset(PAGE_MARGIN, y);
                contentStream.showText(line);
                contentStream.endText();
                y -= size + spacing;
            }
        }

        private List<String> wrap(String text, float size) throws IOException {
            float maxWidth = page.getMediaBox().getWidth() - PAGE_MARGIN * 2;
            List<String> lines = new ArrayList<>();
            String[] paragraphs = text.split("\n", -1);
            for (String paragraph : paragraphs) {
                if (paragraph.isEmpty()) {
                    lines.add("");
                    continue;
                }
                StringBuilder current = new StringBuilder();
                for (int index = 0; index < paragraph.length(); index++) {
                    char ch = paragraph.charAt(index);
                    current.append(ch);
                    if (font.getStringWidth(current.toString()) / 1000 * size > maxWidth && current.length() > 1) {
                        current.deleteCharAt(current.length() - 1);
                        lines.add(current.toString());
                        current = new StringBuilder().append(ch);
                    }
                }
                lines.add(current.toString());
            }
            return lines;
        }

        private String sanitize(String text) throws IOException {
            String normalized = text
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .replace('\t', ' ')
                    .replace('•', '-')
                    .replace('●', '-')
                    .replace('▪', '-')
                    .replace('◆', '-')
                    .replace('–', '-')
                    .replace('—', '-')
                    .replace('“', '"')
                    .replace('”', '"')
                    .replace('‘', '\'')
                    .replace('’', '\'');
            StringBuilder builder = new StringBuilder();
            for (int offset = 0; offset < normalized.length(); ) {
                int codePoint = normalized.codePointAt(offset);
                offset += Character.charCount(codePoint);
                if (codePoint == '\n') {
                    builder.append('\n');
                    continue;
                }
                String candidate = new String(Character.toChars(codePoint));
                if (canEncode(candidate)) {
                    builder.append(candidate);
                } else if (Character.isWhitespace(codePoint)) {
                    builder.append(' ');
                } else {
                    builder.append('?');
                }
            }
            return builder.toString();
        }

        private boolean canEncode(String value) {
            try {
                font.getStringWidth(value);
                return true;
            } catch (IllegalArgumentException exception) {
                return false;
            } catch (IOException exception) {
                return false;
            }
        }

        private void ensure(float height) throws IOException {
            if (y - height < PAGE_MARGIN) {
                startPage();
            }
        }

        private void startPage() throws IOException {
            closeCurrent();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            contentStream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - PAGE_MARGIN;
        }

        private void close() throws IOException {
            closeCurrent();
        }

        private void closeCurrent() throws IOException {
            if (contentStream != null) {
                contentStream.close();
                contentStream = null;
            }
        }
    }
}
