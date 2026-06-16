package com.gsmv.ai.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsmv.ai.agent.AgentOrchestratorService;
import com.gsmv.ai.agent.AgentTask;
import com.gsmv.ai.agent.dto.AgentDtos;
import com.gsmv.ai.rag.RagKnowledgeService;
import com.gsmv.ai.report.dto.AiReportDtos;
import com.gsmv.ai.report.export.AiReportPdfExporter;
import com.gsmv.ai.report.mapper.AiReportMapper;
import com.gsmv.ai.report.model.AiReport;
import com.gsmv.audit.service.AuditService;
import com.gsmv.common.ErrorCode;
import com.gsmv.common.PageResponse;
import com.gsmv.common.exception.BusinessException;
import com.gsmv.common.exception.NotFoundException;
import com.gsmv.report.ReportService;
import com.gsmv.report.dto.DashboardSummary;
import com.gsmv.report.dto.EcosystemAnalyticsPoint;
import com.gsmv.report.dto.NameValuePoint;
import com.gsmv.security.CurrentUser;
import com.gsmv.security.SecurityUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AiReportService {

    private final AiReportMapper aiReportMapper;
    private final ReportService reportService;
    private final RagKnowledgeService ragKnowledgeService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AgentOrchestratorService agentOrchestratorService;

    public AiReportService(
            AiReportMapper aiReportMapper,
            ReportService reportService,
            RagKnowledgeService ragKnowledgeService,
            AuditService auditService,
            ObjectMapper objectMapper,
            AgentOrchestratorService agentOrchestratorService
    ) {
        this.aiReportMapper = aiReportMapper;
        this.reportService = reportService;
        this.ragKnowledgeService = ragKnowledgeService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.agentOrchestratorService = agentOrchestratorService;
    }

    @Transactional
    public AiReportDtos.AiReportDetailView generate(AiReportDtos.GenerateReportRequest request) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        int days = sanitizeDays(request.days());
        String reportType = normalizeReportType(request.reportType(), days);
        GeneratedReport generated = generateBaselineReport(reportType, days);
        AgentDtos.AgentRunView run = agentOrchestratorService.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_RESEARCH_REPORT,
                "AI_RESEARCH_REPORT",
                null,
                generated.title() + " " + generated.summary(),
                RagKnowledgeService.SCENARIO_REPORT,
                mapOf(
                        "reportType", reportType,
                        "days", days,
                        "title", generated.title(),
                        "summary", generated.summary(),
                        "highlights", generated.highlights(),
                        "risks", generated.risks(),
                        "recommendations", generated.recommendations(),
                        "evidence", generated.evidence()
                ),
                List.of()
        ));
        GeneratedReport agentDraft = applyAgentDraft(generated, run.finalOutput());

        AiReport report = new AiReport();
        report.setReportType(reportType);
        report.setDays(days);
        report.setTitle(agentDraft.title());
        report.setSummary(agentDraft.summary());
        report.setHighlightsJson(writeJson(agentDraft.highlights()));
        report.setRisksJson(writeJson(agentDraft.risks()));
        report.setRecommendationsJson(writeJson(agentDraft.recommendations()));
        report.setEvidenceJson(writeJson(agentDraft.evidence()));
        report.setCreatedBy(currentUser.userId());
        aiReportMapper.insert(report);
        report.setAgentRunId(run.id());
        aiReportMapper.updateAgentRunId(report.getId(), run.id());
        agentOrchestratorService.attachSubject(run.id(), "AI_RESEARCH_REPORT", report.getId());
        ragKnowledgeService.syncAiReport(report.getId());

        auditService.record(currentUser.userId(), "AI", "GENERATE_RESEARCH_REPORT", "AI_RESEARCH_REPORT", report.getId(), true,
                "{\"days\":" + days + ",\"reportType\":\"" + escapeJson(reportType) + "\"}");
        return getDetail(report.getId());
    }

    public PageResponse<AiReportDtos.AiReportView> list(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        List<AiReportDtos.AiReportView> items = aiReportMapper.findPage(safeSize, offset).stream()
                .map(this::toView)
                .toList();
        return new PageResponse<>(items, aiReportMapper.count(), safePage, safeSize);
    }

    public AiReportDtos.AiReportDetailView getDetail(Long id) {
        AiReport report = aiReportMapper.findById(id);
        if (report == null) {
            throw new NotFoundException("AI 科研报告不存在");
        }
        return toDetail(report);
    }

    public byte[] exportPdf(Long id) {
        return AiReportPdfExporter.export(getDetail(id));
    }

    private GeneratedReport generateBaselineReport(String reportType, int days) {
        DashboardSummary summary = reportService.dashboardSummary();
        List<NameValuePoint> trend = reportService.observationTrend(days);
        List<EcosystemAnalyticsPoint> ecosystems = reportService.ecosystemAnalytics();
        List<NameValuePoint> protection = reportService.protectionLevelDistribution();
        return new GeneratedReport(
                defaultTitle(reportType, days),
                fallbackSummary(summary, days),
                fallbackHighlights(summary, trend, ecosystems),
                fallbackRisks(summary, protection),
                fallbackRecommendations(),
                fallbackEvidence(summary, days)
        );
    }

    private List<String> fallbackHighlights(
            DashboardSummary summary,
            List<NameValuePoint> trend,
            List<EcosystemAnalyticsPoint> ecosystems
    ) {
        List<String> highlights = new ArrayList<>();
        highlights.add("当前系统累计维护 " + summary.totalSpecies() + " 条物种档案、" + summary.totalObservations() + " 条观测记录。");
        if (!trend.isEmpty()) {
            highlights.add("统计期内最新观测日期为 " + trend.get(trend.size() - 1).name() + "，当天记录 " + trend.get(trend.size() - 1).value() + " 条。");
        }
        ecosystems.stream().findFirst().ifPresent(item ->
                highlights.add("观测最活跃生态系统为 " + item.ecosystemName() + "，累计 " + item.observationCount() + " 次观测。"));
        return highlights;
    }

    private List<String> fallbackRisks(DashboardSummary summary, List<NameValuePoint> protection) {
        List<String> risks = new ArrayList<>();
        if (summary.totalSpecies() == 0 || summary.totalObservations() == 0) {
            risks.add("当前样本量偏少，暂不适合形成趋势性结论。");
        }
        if (protection.stream().anyMatch(item -> item.name() != null && item.name().contains("未"))) {
            risks.add("仍存在保护等级未完善的物种档案，建议补齐后再用于正式报告。");
        }
        if (risks.isEmpty()) {
            risks.add("未发现阻断报告生成的明显数据风险，但仍建议人工复核重点观测。");
        }
        return risks;
    }

    private List<String> fallbackRecommendations() {
        return List.of(
                "优先复核近 30 天新增观测记录中的坐标、物种关联和环境参数。",
                "对高保护等级或濒危状态物种建立定期跟踪清单。",
                "将报告结论与地图点位、原始观测记录一起归档。"
        );
    }

    private List<String> fallbackEvidence(DashboardSummary summary, int days) {
        List<String> evidence = new ArrayList<>(List.of(
                "统计范围：近 " + days + " 天",
                "物种档案总数：" + summary.totalSpecies(),
                "观测记录总数：" + summary.totalObservations(),
                "生态系统总数：" + summary.totalEcosystems()
        ));
        return evidence;
    }

    private String fallbackSummary(DashboardSummary summary, int days) {
        return "近 " + days + " 天内，系统以物种档案、生态系统和观测记录为主要依据生成本报告；当前累计观测 "
                + summary.totalObservations() + " 条，覆盖 " + summary.totalEcosystems() + " 个生态系统。";
    }

    private String defaultTitle(String reportType, int days) {
        return "GSMV " + reportTypeLabel(reportType) + "（近 " + days + " 天）";
    }

    private String reportTypeLabel(String reportType) {
        return switch (reportType) {
            case "MONTHLY" -> "月度科研简报";
            case "WEEKLY" -> "周度科研简报";
            default -> "专题科研简报";
        };
    }

    private AiReportDtos.AiReportView toView(AiReport report) {
        return new AiReportDtos.AiReportView(
                report.getId(),
                report.getReportType(),
                report.getDays() == null ? 30 : report.getDays(),
                report.getTitle(),
                report.getSummary(),
                report.getCreatedBy(),
                report.getCreatorName(),
                report.getCreatedAt()
        );
    }

    private AiReportDtos.AiReportDetailView toDetail(AiReport report) {
        AgentDtos.AgentRunView agentRun = agentOrchestratorService.getRunSnapshot(report.getAgentRunId());
        return new AiReportDtos.AiReportDetailView(
                report.getId(),
                report.getReportType(),
                report.getDays() == null ? 30 : report.getDays(),
                report.getTitle(),
                report.getSummary(),
                readStringList(report.getHighlightsJson()),
                readStringList(report.getRisksJson()),
                readStringList(report.getRecommendationsJson()),
                readStringList(report.getEvidenceJson()),
                report.getCreatedBy(),
                report.getCreatorName(),
                report.getCreatedAt(),
                report.getAgentRunId(),
                agentRun,
                agentRun == null ? List.of() : agentRun.steps(),
                agentRun == null ? null : agentRun.verificationStatus(),
                agentRun == null ? null : agentRun.confidence()
        );
    }

    private int sanitizeDays(Integer days) {
        int value = days == null ? 30 : days;
        return Math.min(Math.max(value, 1), 365);
    }

    private String normalizeReportType(String reportType, int days) {
        if (!StringUtils.hasText(reportType)) {
            return days <= 7 ? "WEEKLY" : days <= 31 ? "MONTHLY" : "CUSTOM";
        }
        String normalized = reportType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("WEEKLY", "MONTHLY", "CUSTOM").contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报告类型仅支持 WEEKLY、MONTHLY、CUSTOM", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private GeneratedReport applyAgentDraft(GeneratedReport fallback, Object finalOutput) {
        Map<String, Object> output = asStringMap(finalOutput);
        Map<String, Object> draft = asStringMap(output.get("finalDraft"));
        if (draft.isEmpty()) {
            return fallback;
        }
        return new GeneratedReport(
                firstNonBlank(textValue(draft.get("title")), fallback.title()),
                firstNonBlank(textValue(draft.get("summary")), fallback.summary()),
                nonEmptyStringList(draft.get("highlights"), fallback.highlights()),
                nonEmptyStringList(draft.get("risks"), fallback.risks()),
                nonEmptyStringList(draft.get("recommendations"), fallback.recommendations()),
                nonEmptyStringList(draft.get("evidence"), fallback.evidence())
        );
    }

    private List<String> nonEmptyStringList(Object value, List<String> fallback) {
        List<String> values = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            collection.stream()
                    .map(this::textValue)
                    .filter(StringUtils::hasText)
                    .forEach(values::add);
        } else if (value != null) {
            String text = textValue(value);
            if (StringUtils.hasText(text)) {
                values.add(text);
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    private Map<String, Object> asStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        map.forEach((key, entryValue) -> {
            if (key != null && entryValue != null) {
                values.put(String.valueOf(key), entryValue);
            }
        });
        return values;
    }

    private String textValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 报告保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            Object key = pairs[index];
            Object value = pairs[index + 1];
            if (key != null && value != null) {
                values.put(String.valueOf(key), value);
            }
        }
        return values;
    }

    private record GeneratedReport(
            String title,
            String summary,
            List<String> highlights,
            List<String> risks,
            List<String> recommendations,
            List<String> evidence
    ) {
    }
}
