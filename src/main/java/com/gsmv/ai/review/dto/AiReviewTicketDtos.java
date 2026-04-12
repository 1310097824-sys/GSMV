package com.gsmv.ai.review.dto;

import com.gsmv.ai.dto.SpeciesAiDtos;
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
