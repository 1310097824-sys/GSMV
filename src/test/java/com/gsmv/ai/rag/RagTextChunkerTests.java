package com.gsmv.ai.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RagTextChunkerTests {

    @Test
    void shouldSplitLongTextWithReadableSummary() {
        RagTextChunker chunker = new RagTextChunker();
        String text = "湛江近海海草床记录中华白海豚、绿海龟和珊瑚礁鱼类。".repeat(80);

        List<RagTextChunker.ChunkDraft> chunks = chunker.chunk("湛江样带", text);

        assertFalse(chunks.isEmpty());
        assertEquals(0, chunks.get(0).index());
        assertTrue(chunks.get(0).summary().length() <= 183);
        assertTrue(chunks.stream().allMatch(item -> item.content().length() <= 700));
    }
}
