package com.gsmv.report.export;

import com.gsmv.report.dto.DashboardSummary;
import com.gsmv.report.dto.EcosystemAnalyticsPoint;
import com.gsmv.report.dto.NameValuePoint;
import com.gsmv.report.dto.ObservationMapPoint;
import com.gsmv.report.dto.ReportExportSnapshot;
import com.gsmv.report.dto.SpeciesDistributionPoint;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

public final class ReportPdfExporter {

    private static final float PAGE_MARGIN = 32F;
    private static final float DEFAULT_FONT_SIZE = 10F;
    private static final float TITLE_FONT_SIZE = 18F;
    private static final float SECTION_FONT_SIZE = 13F;
    private static final float LINE_SPACING = 14F;

    private ReportPdfExporter() {
    }

    public static byte[] export(ReportExportSnapshot snapshot) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDFont font = loadFont(document);
            PdfLineWriter writer = new PdfLineWriter(document, font);

            writer.writeTitle("GSMV 数据分析报表");
            writeSummary(writer, snapshot.summary());
            writeNameValueSection(writer, "保护等级分布", "保护等级", snapshot.protectionLevelDistribution());
            writeNameValueSection(writer, "濒危状态分布", "濒危状态", snapshot.iucnStatusDistribution());
            writeNameValueSection(writer, "物种门级分布", "门", snapshot.speciesPhylumDistribution());
            writeNameValueSection(writer, "物种纲级分布", "纲", snapshot.speciesClassDistribution());
            writeNameValueSection(writer, "近 30 天观测趋势", "日期", snapshot.observationTrend());
            writeNameValueSection(writer, "观测人员活跃度", "观测人员", snapshot.observationActivityByUser());
            writeEcosystemSection(writer, snapshot.ecosystemAnalytics());
            writeSpeciesMapSection(writer, snapshot.speciesDistributionPoints());
            writeObservationMapSection(writer, snapshot.observationMapPoints());

            writer.close();
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("导出 PDF 失败", exception);
        }
    }

    private static PDFont loadFont(PDDocument document) throws IOException {
        List<Path> candidates = List.of(
                Path.of("C:/Windows/Fonts/simhei.ttf"),
                Path.of("C:/Windows/Fonts/NotoSansSC-VF.ttf"),
                Path.of("C:/Windows/Fonts/simsunb.ttf")
        );

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return PDType0Font.load(document, candidate.toFile());
            }
        }
        return PDType1Font.HELVETICA;
    }

    private static void writeSummary(PdfLineWriter writer, DashboardSummary summary) throws IOException {
        writer.writeSection("系统概览");
        writer.writeKeyValue("物种总数", String.valueOf(summary.totalSpecies()));
        writer.writeKeyValue("观测次数", String.valueOf(summary.totalObservations()));
        writer.writeKeyValue("生态系统数量", String.valueOf(summary.totalEcosystems()));
        writer.writeKeyValue("活跃用户数", String.valueOf(summary.totalUsers()));
        writer.writeKeyValue("近 7 天观测次数", String.valueOf(summary.recentObservationCount()));
        writer.writeBlankLine();
    }

    private static void writeNameValueSection(
            PdfLineWriter writer,
            String title,
            String nameHeader,
            List<NameValuePoint> rows
    ) throws IOException {
        writer.writeSection(title);
        writer.writeLine(nameHeader + " | 数量");
        for (NameValuePoint row : rows) {
            writer.writeLine(nullSafe(row.name()) + " | " + row.value());
        }
        writer.writeBlankLine();
    }

    private static void writeEcosystemSection(PdfLineWriter writer, List<EcosystemAnalyticsPoint> rows) throws IOException {
        writer.writeSection("生态系统统计");
        writer.writeLine("生态系统 | 类型 | 观测次数 | 发现物种数");
        for (EcosystemAnalyticsPoint row : rows) {
            writer.writeLine(
                    nullSafe(row.ecosystemName()) + " | " +
                    nullSafe(row.ecosystemType()) + " | " +
                    row.observationCount() + " | " +
                    row.speciesCount()
            );
        }
        writer.writeBlankLine();
    }

    private static void writeSpeciesMapSection(PdfLineWriter writer, List<SpeciesDistributionPoint> rows) throws IOException {
        writer.writeSection("物种分布点位");
        writer.writeLine("中文名 | 学名 | 坐标 | 地理范围 | 保护等级 | 濒危状态");
        for (SpeciesDistributionPoint row : rows) {
            writer.writeLine(
                    nullSafe(row.chineseName()) + " | " +
                    nullSafe(row.scientificName()) + " | " +
                    toCoordinate(row.locationLat(), row.locationLng()) + " | " +
                    nullSafe(row.geoRangeText()) + " | " +
                    nullSafe(row.protectionLevel()) + " | " +
                    nullSafe(row.iucnStatus())
            );
        }
        writer.writeBlankLine();
    }

    private static void writeObservationMapSection(PdfLineWriter writer, List<ObservationMapPoint> rows) throws IOException {
        writer.writeSection("观测地点概要");
        writer.writeLine("地点 | 生态系统 | 观测人员 | 观测时间 | 物种数 | 备注");
        for (ObservationMapPoint row : rows) {
            writer.writeLine(
                    nullSafe(row.locationName()) + " | " +
                    nullSafe(row.ecosystemName()) + " | " +
                    nullSafe(row.observerName()) + " | " +
                    toText(row.observedAt()) + " | " +
                    row.speciesCount() + " | " +
                    nullSafe(row.note())
            );
        }
        writer.writeBlankLine();
    }

    private static String toCoordinate(BigDecimal lat, BigDecimal lng) {
        return (lat == null ? "" : lat.toPlainString()) + ", " + (lng == null ? "" : lng.toPlainString());
    }

    private static String toText(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static final class PdfLineWriter {

        private final PDDocument document;
        private final PDFont font;
        private PDPage page;
        private PDPageContentStream contentStream;
        private float y;

        private PdfLineWriter(PDDocument document, PDFont font) throws IOException {
            this.document = document;
            this.font = font;
            startPage();
        }

        private void writeTitle(String text) throws IOException {
            writeWrapped(text, TITLE_FONT_SIZE, true);
            writeBlankLine();
        }

        private void writeSection(String text) throws IOException {
            writeWrapped(text, SECTION_FONT_SIZE, true);
        }

        private void writeKeyValue(String key, String value) throws IOException {
            writeWrapped(key + ": " + value, DEFAULT_FONT_SIZE, false);
        }

        private void writeLine(String text) throws IOException {
            writeWrapped(text, DEFAULT_FONT_SIZE, false);
        }

        private void writeBlankLine() throws IOException {
            ensureSpace(LINE_SPACING);
            y -= LINE_SPACING;
        }

        private void writeWrapped(String text, float fontSize, boolean emphasize) throws IOException {
            float maxWidth = PDRectangle.A4.getHeight() - PAGE_MARGIN * 2;
            List<String> lines = wrapText(text, fontSize, maxWidth);
            for (String line : lines) {
                ensureSpace(fontSize + 4);
                contentStream.beginText();
                contentStream.setFont(font, fontSize);
                contentStream.newLineAtOffset(PAGE_MARGIN, y);
                contentStream.showText(line);
                contentStream.endText();
                y -= fontSize + (emphasize ? 6 : 4);
            }
        }

        private List<String> wrapText(String text, float fontSize, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            if (text == null || text.isEmpty()) {
                lines.add("");
                return lines;
            }

            StringBuilder current = new StringBuilder();
            for (int index = 0; index < text.length(); index++) {
                char currentChar = text.charAt(index);
                current.append(currentChar);
                float width = font.getStringWidth(current.toString()) / 1000 * fontSize;
                if (width > maxWidth && current.length() > 1) {
                    current.deleteCharAt(current.length() - 1);
                    lines.add(current.toString());
                    current = new StringBuilder().append(currentChar);
                }
            }

            if (!current.isEmpty()) {
                lines.add(current.toString());
            }

            return lines;
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (y - requiredHeight >= PAGE_MARGIN) {
                return;
            }
            startPage();
        }

        private void startPage() throws IOException {
            closeCurrentStream();
            page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            document.addPage(page);
            contentStream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - PAGE_MARGIN;
        }

        private void close() throws IOException {
            closeCurrentStream();
        }

        private void closeCurrentStream() throws IOException {
            if (contentStream != null) {
                contentStream.close();
                contentStream = null;
            }
        }
    }
}
