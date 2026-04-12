package com.gsmv.species.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SpeciesRow(
        Long id,
        Long taxonId,
        String rank,
        String scientificName,
        String chineseName,
        String protectionLevel,
        String iucnStatus,
        String description,
        String morphology,
        String habit,
        String habitat,
        String distribution,
        BigDecimal distributionLat,
        BigDecimal distributionLng,
        String geoRangeText,
        String videoUrl,
        String referenceText,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
