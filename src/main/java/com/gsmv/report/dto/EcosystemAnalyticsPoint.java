package com.gsmv.report.dto;

public record EcosystemAnalyticsPoint(
        Long ecosystemId,
        String ecosystemName,
        String ecosystemType,
        long observationCount,
        long speciesCount
) {
}
