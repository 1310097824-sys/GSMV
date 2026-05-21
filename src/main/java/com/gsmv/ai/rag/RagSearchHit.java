package com.gsmv.ai.rag;

public record RagSearchHit(
        Long chunkId,
        Long documentId,
        String sourceType,
        Long sourceId,
        String title,
        String summary,
        String content,
        double score,
        double cosineScore,
        double keywordScore,
        String sourcePath
) {
}
