package com.gsmv.ai.rag;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RagTextExtractor {

    public String extract(byte[] bytes, String filename, String contentType) {
        String lowerName = filename == null ? "" : filename.toLowerCase();
        String lowerType = contentType == null ? "" : contentType.toLowerCase();
        try {
            if (lowerName.endsWith(".pdf") || lowerType.contains("pdf")) {
                return extractPdf(bytes);
            }
            if (lowerName.endsWith(".docx") || lowerType.contains("wordprocessingml")) {
                return extractDocx(bytes);
            }
            if (lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerType.startsWith("text/")) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            throw new IllegalArgumentException("仅支持 PDF、DOCX、TXT、MD 文档");
        } catch (IOException ex) {
            throw new IllegalStateException("文档文本抽取失败: " + ex.getMessage(), ex);
        }
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = PDDocument.load(bytes)) {
            String text = new PDFTextStripper().getText(document);
            if (!StringUtils.hasText(text)) {
                throw new IllegalArgumentException("该 PDF 未抽取到文本，可能是扫描版文件，需要可复制文本的 PDF");
            }
            return text;
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder builder = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> {
                String text = paragraph.getText();
                if (StringUtils.hasText(text)) {
                    builder.append(text).append('\n');
                }
            });
            String text = builder.toString();
            if (!StringUtils.hasText(text)) {
                throw new IllegalArgumentException("该 DOCX 未抽取到有效文本");
            }
            return text;
        }
    }
}
