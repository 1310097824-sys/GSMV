package com.gsmv.species.dto;

import java.time.LocalDateTime;

public record SpeciesView(
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
        String geoRangeText,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
