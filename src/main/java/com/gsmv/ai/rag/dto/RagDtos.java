package com.gsmv.ai.rag.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

public final class RagDtos {
    private RagDtos() {
    }

    public record RagDocumentView(
            Long id,
            String sourceType,
            Long sourceId,
            Long mediaId,
            String title,
            String originalFilename,
            String contentType,
            String status,
            Integer chunkCount,
            String errorMessage,
            Long uploadedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record RagChunkView(
            Long id,
            Long documentId,
            String sourceType,
            Long sourceId,
            Integer chunkIndex,
            String title,
            String summary,
            String content,
            String vectorPointId,
            String embeddingStatus,
            String embeddingError,
            Integer characterCount,
            String status,
            LocalDateTime createdAt
    ) {
    }

    public record RagDocumentDetailView(
            RagDocumentView document,
            List<RagChunkView> chunks
    ) {
    }

    public record RagIndexJobView(
            Long id,
            String jobType,
            String status,
            String targetSourceType,
            Long targetSourceId,
            Integer totalDocuments,
            Integer totalChunks,
            Integer successCount,
            Integer failedCount,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            Long createdBy,
            LocalDateTime createdAt
    ) {
    }

    public record SearchTestRequest(
            @NotBlank(message = "请输入检索问题") String query,
            Integer limit
    ) {
    }

    public record RagSearchResultView(
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

    public record RagEvidenceItem(
            String sourceType,
            Long sourceId,
            Long documentId,
            Long chunkId,
            String title,
            String summary,
            String contentSnippet,
            double score,
            String sourcePath,
            String sourceName,
            String scenario
    ) {
    }

    public record FolderIngestRequest(
            @NotBlank(message = "Please input a local folder path") String path,
            Boolean recursive
    ) {
    }

    public record ExternalIngestRequest(
            @NotBlank(message = "Please choose a source") String sourceCode,
            String query,
            Integer limit,
            List<String> urls
    ) {
    }

    public record RagIngestJobView(
            Long id,
            String jobType,
            String status,
            String sourceCode,
            String title,
            Integer totalItems,
            Integer processedItems,
            Integer successCount,
            Integer failedCount,
            String errorMessage,
            Long createdBy,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt
    ) {
    }

    public record RagIngestItemView(
            Long id,
            Long jobId,
            String sourceType,
            String sourceCode,
            String externalId,
            String sourceUrl,
            String localPath,
            Long mediaId,
            Long ragDocumentId,
            String title,
            String status,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record RagSourceView(
            Long id,
            String code,
            String name,
            String sourceType,
            String baseUrl,
            Boolean enabled
    ) {
    }

    public record QdrantStatusView(
            boolean available,
            String status,
            long pointsCount,
            long readyChunks,
            String errorMessage
    ) {
    }
}
