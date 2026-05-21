package com.gsmv.ai.review.dto;

import com.gsmv.ai.dto.SpeciesAiDtos;
import com.gsmv.ai.rag.dto.RagDtos;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

public final class AiReviewTicketDtos {

    private AiReviewTicketDtos() {
    }

    public record CreateReviewTicketRequest(
            String likelyChineseName,
            String likelyScientificName,
            double confidence,
            boolean needsHumanReview,
            String reasoning,
            List<SpeciesAiDtos.IdentificationCandidate> candidates,
            List<SpeciesAiDtos.RelatedSpeciesRecord> relatedSpeciesRecords,
            List<RagDtos.RagEvidenceItem> ragEvidence,
            String ragConclusion,
            List<String> conflictWarnings,
            String submitNote
    ) {
    }

    public record ResolveReviewTicketRequest(
            @NotBlank(message = "请选择复核结论") String resolutionCode,
            Long finalSpeciesId,
            String finalChineseName,
            String finalScientificName,
            @NotBlank(message = "请填写复核说明") String reviewNote
    ) {
    }

    public record RejectReviewTicketRequest(
            @NotBlank(message = "请填写驳回说明") String reviewNote
    ) {
    }

    public record ResubmitReviewTicketRequest(
            String submitNote
    ) {
    }

    public record LinkSpeciesRequest(
            Long finalSpeciesId,
            @NotBlank(message = "请填写关联说明") String reviewNote
    ) {
    }

    public record ReviewTicketView(
            Long id,
            String sourceType,
            String status,
            String resolutionCode,
            Long submittedBy,
            String submittedByName,
            Long reviewerUserId,
            String reviewerName,
            String likelyChineseName,
            String likelyScientificName,
            double confidence,
            boolean needsHumanReview,
            Long imageMediaId,
            String imageUrl,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ReviewTicketDetailView(
            Long id,
            String sourceType,
            String status,
            String resolutionCode,
            Long submittedBy,
            String submittedByName,
            Long reviewerUserId,
            String reviewerName,
            Long imageMediaId,
            String imageUrl,
            String likelyChineseName,
            String likelyScientificName,
            double confidence,
            boolean needsHumanReview,
            String reasoning,
            List<SpeciesAiDtos.IdentificationCandidate> candidates,
            List<SpeciesAiDtos.RelatedSpeciesRecord> relatedSpeciesRecords,
            List<RagDtos.RagEvidenceItem> ragEvidence,
            String initialRecognitionJson,
            String reviewEvidenceJson,
            String submitNote,
            Long finalSpeciesId,
            String finalChineseName,
            String finalScientificName,
            String reviewNote,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
