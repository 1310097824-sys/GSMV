package com.gsmv.ai.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RagVectorUtilsTests {

    @Test
    void cosineShouldScoreIdenticalVectorHighest() {
        double identical = RagVectorUtils.cosine(List.of(1.0, 0.0, 1.0), List.of(1.0, 0.0, 1.0));
        double different = RagVectorUtils.cosine(List.of(1.0, 0.0, 0.0), List.of(0.0, 1.0, 0.0));

        assertEquals(1.0, identical, 0.0001);
        assertEquals(0.0, different, 0.0001);
    }

    @Test
    void keywordScoreShouldMatchChineseTerms() {
        double score = RagVectorUtils.keywordScore("湛江近海 高保护", "湛江近海记录了国家一级保护物种。");

        assertTrue(score > 0.0);
    }
}
