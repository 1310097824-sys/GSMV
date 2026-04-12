package com.gsmv.report;

import com.gsmv.common.ApiResponse;
import com.gsmv.report.dto.DashboardSummary;
import com.gsmv.report.dto.EcosystemAnalyticsPoint;
import com.gsmv.report.dto.NameValuePoint;
import com.gsmv.report.dto.ObservationMapPoint;
import com.gsmv.report.dto.SpeciesDistributionPoint;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<DashboardSummary> summary() {
        return ApiResponse.success(reportService.dashboardSummary());
    }

    @GetMapping("/protection-level")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<List<NameValuePoint>> protectionLevelDistribution() {
        return ApiResponse.success(reportService.protectionLevelDistribution());
    }

    @GetMapping("/iucn-status")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<List<NameValuePoint>> iucnStatusDistribution() {
        return ApiResponse.success(reportService.iucnStatusDistribution());
    }

    @GetMapping("/taxonomy/phylum")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<List<NameValuePoint>> speciesPhylumDistribution() {
        return ApiResponse.success(reportService.speciesPhylumDistribution());
    }

    @GetMapping("/taxonomy/class")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<List<NameValuePoint>> speciesClassDistribution() {
        return ApiResponse.success(reportService.speciesClassDistribution());
    }

    @GetMapping("/observation-trend")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<List<NameValuePoint>> observationTrend(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(reportService.observationTrend(days));
    }

    @GetMapping("/observation-activity")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<List<NameValuePoint>> observationActivity(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(reportService.observationActivityByUser(days));
    }

    @GetMapping("/ecosystem-analytics")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<List<EcosystemAnalyticsPoint>> ecosystemAnalytics() {
        return ApiResponse.success(reportService.ecosystemAnalytics());
    }

    @GetMapping("/species-distribution")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<List<SpeciesDistributionPoint>> speciesDistribution() {
        return ApiResponse.success(reportService.speciesDistributionPoints());
    }

    @GetMapping("/observation-map")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<List<ObservationMapPoint>> observationMap() {
        return ApiResponse.success(reportService.observationMapPoints());
    }

    @GetMapping("/export/excel")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, buildDisposition("gsmv-report-" + fileDate() + ".xlsx"))
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(reportService.exportExcel(days));
    }

    @GetMapping("/export/pdf")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ResponseEntity<byte[]> exportPdf(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, buildDisposition("gsmv-report-" + fileDate() + ".pdf"))
                .contentType(MediaType.APPLICATION_PDF)
                .body(reportService.exportPdf(days));
    }

    private String buildDisposition(String fileName) {
        return ContentDisposition.attachment().filename(fileName).build().toString();
    }

    private String fileDate() {
        return FILE_DATE_FORMAT.format(LocalDate.now());
    }
}
