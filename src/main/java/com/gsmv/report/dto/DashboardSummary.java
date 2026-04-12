package com.gsmv.report.dto;

public record DashboardSummary(
        long totalSpecies,
        long totalObservations,
        long totalEcosystems,
        long totalUsers,
        long recentObservationCount
) {
}
