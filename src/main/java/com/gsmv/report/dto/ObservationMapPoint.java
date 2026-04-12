package com.gsmv.report.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ObservationMapPoint(
        Long observationId,
        String ecosystemName,
        String observerName,
        LocalDateTime observedAt,
        BigDecimal locationLat,
        BigDecimal locationLng,
        String locationName,
        long speciesCount,
        String note
) {
}
