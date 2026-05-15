package com.gsmv.ai.report;

import com.gsmv.ai.report.dto.AiReportDtos;
import com.gsmv.common.ApiResponse;
import com.gsmv.common.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/reports")
public class AiReportController {

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final AiReportService aiReportService;

    public AiReportController(AiReportService aiReportService) {
        this.aiReportService = aiReportService;
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<AiReportDtos.AiReportDetailView> generate(@Valid @RequestBody AiReportDtos.GenerateReportRequest request) {
        return ApiResponse.success(aiReportService.generate(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<PageResponse<AiReportDtos.AiReportView>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(aiReportService.list(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<AiReportDtos.AiReportDetailView> detail(@PathVariable Long id) {
        return ApiResponse.success(aiReportService.getDetail(id));
    }

    @GetMapping("/{id}/export/pdf")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("gsmv-ai-report-" + FILE_DATE_FORMAT.format(LocalDate.now()) + ".pdf")
                        .build()
                        .toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(aiReportService.exportPdf(id));
    }
}
