package com.gsmv.ai.report.dto;

import com.gsmv.ai.agent.dto.AgentDtos;
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
            LocalDateTime createdAt,
            Long agentRunId,
            AgentDtos.AgentRunView agentRun,
            List<AgentDtos.AgentStepView> agentSteps,
            String verificationStatus,
            Double confidence
    ) {
        public AiReportDetailView(
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
            this(id, reportType, days, title, summary, highlights, risks, recommendations, evidence, createdBy, creatorName, createdAt, null, null, List.of(), null, null);
        }
    }
}
