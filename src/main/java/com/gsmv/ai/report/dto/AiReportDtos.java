package com.gsmv.ai.report.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.List;

public final class AiReportDtos {

    private AiReportDtos() {
    }

    public record GenerateReportRequest(
            String reportType,
            @Min(value = 1, message = "统计天数不能小于 1") @Max(value = 365, message = "统计天数不能大于 365") Integer days
    ) {
    }

    public record AiReportView(
            Long id,
            String reportType,
            int days,
            String title,
            String summary,
            Long createdBy,
            String creatorName,
            LocalDateTime createdAt
    ) {
    }

    public record AiReportDetailView(
            Long id,
            String reportType,
            int days,
            String title,
            String summary,
            List<String> highlights,
            List<String> risks,
            List<String> recommendations,
            List<String> evidence,
            Long createdBy,
            String creatorName,
            LocalDateTime createdAt
    ) {
    }
}
