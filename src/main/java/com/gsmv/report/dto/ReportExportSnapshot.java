package com.gsmv.report.dto;

import java.util.List;

public record ReportExportSnapshot(
        DashboardSummary summary,
        List<NameValuePoint> protectionLevelDistribution,
        List<NameValuePoint> iucnStatusDistribution,
        List<NameValuePoint> speciesPhylumDistribution,
        List<NameValuePoint> speciesClassDistribution,
        List<NameValuePoint> observationTrend,
        List<NameValuePoint> observationActivityByUser,
        List<EcosystemAnalyticsPoint> ecosystemAnalytics,
        List<SpeciesDistributionPoint> speciesDistributionPoints,
        List<ObservationMapPoint> observationMapPoints
) {
}
