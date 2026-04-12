package com.gsmv.report;

import com.gsmv.report.dto.DashboardSummary;
import com.gsmv.report.dto.EcosystemAnalyticsPoint;
import com.gsmv.report.dto.NameValuePoint;
import com.gsmv.report.dto.ObservationMapPoint;
import com.gsmv.report.dto.ReportExportSnapshot;
import com.gsmv.report.dto.SpeciesDistributionPoint;
import com.gsmv.report.export.ReportExcelExporter;
import com.gsmv.report.export.ReportPdfExporter;
import com.gsmv.report.mapper.ReportMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final ReportMapper reportMapper;

    public ReportService(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    public DashboardSummary dashboardSummary() {
        return new DashboardSummary(
                reportMapper.countActiveSpecies(),
                reportMapper.countObservations(),
                reportMapper.countEcosystems(),
                reportMapper.countActiveUsers(),
                reportMapper.countRecentObservations()
        );
    }

    public List<NameValuePoint> protectionLevelDistribution() {
        return reportMapper.protectionLevelDistribution();
    }

    public List<NameValuePoint> iucnStatusDistribution() {
        return reportMapper.iucnStatusDistribution();
    }

    public List<NameValuePoint> speciesPhylumDistribution() {
        return reportMapper.speciesPhylumDistribution();
    }

    public List<NameValuePoint> speciesClassDistribution() {
        return reportMapper.speciesClassDistribution();
    }

    public List<NameValuePoint> observationTrend(int days) {
        return reportMapper.observationTrend(sanitizeDays(days));
    }

    public List<NameValuePoint> observationActivityByUser(int days) {
        return reportMapper.observationActivityByUser(sanitizeDays(days));
    }

    public List<EcosystemAnalyticsPoint> ecosystemAnalytics() {
        return reportMapper.ecosystemAnalytics();
    }

    public List<SpeciesDistributionPoint> speciesDistributionPoints() {
        return reportMapper.speciesDistributionPoints();
    }

    public List<ObservationMapPoint> observationMapPoints() {
        return reportMapper.observationMapPoints();
    }

    public byte[] exportExcel(int days) {
        return ReportExcelExporter.export(buildSnapshot(days));
    }

    public byte[] exportPdf(int days) {
        return ReportPdfExporter.export(buildSnapshot(days));
    }

    private ReportExportSnapshot buildSnapshot(int days) {
        int safeDays = sanitizeDays(days);
        return new ReportExportSnapshot(
                dashboardSummary(),
                protectionLevelDistribution(),
                iucnStatusDistribution(),
                speciesPhylumDistribution(),
                speciesClassDistribution(),
                observationTrend(safeDays),
                observationActivityByUser(safeDays),
                ecosystemAnalytics(),
                speciesDistributionPoints(),
                observationMapPoints()
        );
    }

    private int sanitizeDays(int days) {
        return Math.min(Math.max(days, 1), 365);
    }
}
