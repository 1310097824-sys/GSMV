package com.gsmv.report.dto;

import java.math.BigDecimal;

public record SpeciesDistributionPoint(
        Long speciesId,
        String scientificName,
        String chineseName,
        BigDecimal locationLat,
        BigDecimal locationLng,
        String geoRangeText,
        String protectionLevel,
        String iucnStatus
) {
}
