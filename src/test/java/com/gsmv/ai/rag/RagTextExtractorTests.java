package com.gsmv.ai.rag;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RagTextExtractorTests {

    @Test
    void shouldReadPlainTextDocument() {
        RagTextExtractor extractor = new RagTextExtractor();
        String text = extractor.extract("红树林生态系统可作为近岸生物多样性监测样带。".getBytes(StandardCharsets.UTF_8), "note.md", "text/markdown");

        assertTrue(text.contains("红树林生态系统"));
    }
}
