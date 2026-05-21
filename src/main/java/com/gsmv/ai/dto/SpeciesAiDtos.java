package com.gsmv.ai.dto;

import com.gsmv.ai.rag.dto.RagDtos;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class SpeciesAiDtos {

    private SpeciesAiDtos() {
    }

    public record RelatedSpeciesRecord(
            Long id,
            String chineseName,
            String scientificName,
            String classificationPath,
            String protectionLevel,
            String iucnStatus
    ) {
    }

    public record IdentificationCandidate(
            String chineseName,
            String scientificName,
            double confidence,
            String reason
    ) {
    }

    public record IdentifyImageResponse(
            String likelyChineseName,
            String likelyScientificName,
            double confidence,
            boolean needsHumanReview,
            String confidenceLabel,
            String reasoning,
            List<IdentificationCandidate> candidates,
            List<RelatedSpeciesRecord> relatedSpeciesRecords,
            List<RagDtos.RagEvidenceItem> ragEvidence,
            boolean confidenceAdjustedByRag,
            String ragConclusion,
            List<String> conflictWarnings
    ) {
    }

    public record AutocompleteRequest(
            String chineseName,
            String scientificName,
            String description,
            String morphology,
            String habit,
            String habitat,
            String distribution,
            String geoRangeText
    ) {
    }

    public record AutocompleteResponse(
            String chineseName,
            String scientificName,
            String phylumName,
            String className,
            String orderName,
            String familyName,
            String genusName,
            String protectionLevel,
            String iucnStatus,
            String description,
            String morphology,
            String habit,
            String habitat,
            String distribution,
            String geoRangeText,
            String summary,
            double confidence,
            List<String> notes,
            List<RelatedSpeciesRecord> relatedSpeciesRecords
    ) {
    }

    public record PolishTextRequest(
            @NotBlank(message = "请指定需要润色的字段") String fieldName,
            @NotBlank(message = "请先输入需要润色的文本") String text
    ) {
    }

    public record PolishTextResponse(
            String fieldName,
            String polishedText,
            String summary,
            List<String> keywords
    ) {
    }

    public record TranslateSpeciesRequest(
            String chineseName,
            String scientificName,
            String description,
            String morphology,
            String habit,
            String habitat,
            String distribution,
            String geoRangeText,
            @NotBlank(message = "请选择目标语言") String targetLanguage
    ) {
    }

    public record TranslateSpeciesResponse(
            String targetLanguage,
            String description,
            String morphology,
            String habit,
            String habitat,
            String distribution,
            String geoRangeText,
            String summary
    ) {
    }
}
