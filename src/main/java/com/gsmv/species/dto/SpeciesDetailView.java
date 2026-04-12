package com.gsmv.species.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SpeciesDetailView(
        Long id,
        Long taxonId,
        String rank,
        String scientificName,
        String chineseName,
        String phylumName,
        String className,
        String orderName,
        String familyName,
        String genusName,
        String classificationPath,
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
        LocalDateTime updatedAt,
        List<SpeciesImageView> images
) {
}
