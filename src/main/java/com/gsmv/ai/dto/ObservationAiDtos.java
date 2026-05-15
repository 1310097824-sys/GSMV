package com.gsmv.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ObservationAiDtos {

    private ObservationAiDtos() {
    }

    public record EnvironmentSnapshot(
            BigDecimal waterTemperature,
            BigDecimal salinity,
            BigDecimal ph,
            BigDecimal dissolvedOxygen,
            BigDecimal transparency,
            BigDecimal depthMeters,
            String weather,
            String seaState
    ) {
    }

    public record SpeciesObservationItem(
            Long speciesId,
            String scientificName,
            String chineseName,
            Integer countEstimated,
            String behavior,
            String comment
    ) {
    }

    public record AnalyzeObservationRequest(
            Long ecosystemId,
            @NotBlank(message = "请填写生态系统名称") String ecosystemName,
            @NotNull(message = "请填写观测时间") LocalDateTime observedAt,
            @NotNull(message = "请填写纬度") BigDecimal locationLat,
            @NotNull(message = "请填写经度") BigDecimal locationLng,
            String locationName,
            String note,
            @Valid EnvironmentSnapshot environment,
            @Valid List<SpeciesObservationItem> speciesItems
    ) {
    }

    public record ObservationAnomaly(
            String severity,
            String speciesName,
            String message,
            String suggestion,
            Double distanceKm
    ) {
    }

    public record AnalyzeObservationResponse(
            String summary,
            List<String> tags,
            List<String> reviewNotes,
            List<ObservationAnomaly> anomalies,
            boolean needsReview
    ) {
    }

    public record QualityIssue(
            String severity,
            String title,
            String message,
            String suggestion
    ) {
    }

    public record QualityCheckResponse(
            Long observationId,
            int score,
            String grade,
            String summary,
            List<String> strengths,
            List<QualityIssue> issues,
            boolean needsReview
    ) {
    }
}
