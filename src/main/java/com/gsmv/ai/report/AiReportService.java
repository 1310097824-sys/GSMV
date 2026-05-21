package com.gsmv.ai.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsmv.ai.AiModelGateway;
import com.gsmv.ai.rag.RagKnowledgeService;
import com.gsmv.ai.rag.RagSearchHit;
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
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AiReportService {

    private final AiReportMapper aiReportMapper;
    private final ReportService reportService;
    private final AiModelGateway aiModelGateway;
    private final RagKnowledgeService ragKnowledgeService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AiReportService(
            AiReportMapper aiReportMapper,
            ReportService reportService,
            AiModelGateway aiModelGateway,
            RagKnowledgeService ragKnowledgeService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.aiReportMapper = aiReportMapper;
        this.reportService = reportService;
        this.aiModelGateway = aiModelGateway;
        this.ragKnowledgeService = ragKnowledgeService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiReportDtos.AiReportDetailView generate(AiReportDtos.GenerateReportRequest request) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        int days = sanitizeDays(request.days());
        String reportType = normalizeReportType(request.reportType(), days);
        GeneratedReport generated = generateWithAi(reportType, days);

        AiReport report = new AiReport();
        report.setReportType(reportType);
        report.setDays(days);
        report.setTitle(generated.title());
        report.setSummary(generated.summary());
        report.setHighlightsJson(writeJson(generated.highlights()));
        report.setRisksJson(writeJson(generated.risks()));
        report.setRecommendationsJson(writeJson(generated.recommendations()));
        report.setEvidenceJson(writeJson(generated.evidence()));
        report.setCreatedBy(currentUser.userId());
        aiReportMapper.insert(report);
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

    private GeneratedReport generateWithAi(String reportType, int days) {
        DashboardSummary summary = reportService.dashboardSummary();
        List<NameValuePoint> trend = reportService.observationTrend(days);
        List<NameValuePoint> observers = reportService.observationActivityByUser(days);
        List<EcosystemAnalyticsPoint> ecosystems = reportService.ecosystemAnalytics();
        List<NameValuePoint> protection = reportService.protectionLevelDistribution();
        List<RagSearchHit> ragHits = ragKnowledgeService.retrieveForScenario(
                RagKnowledgeService.SCENARIO_REPORT,
                "海洋生物多样性科研报告 " + reportType + " 近" + days + "天 重点发现 风险 建议",
                6
        );
        String context = buildContext(summary, trend, observers, ecosystems, protection, ragHits);

        try {
            JsonNode node = aiModelGateway.deepSeekJson(List.of(
                    AiModelGateway.message("system", """
                            你是海洋生物多样性科研报告助手。请基于系统统计数据生成简洁、可复核的中文科研简报。
                            只返回 JSON，不要 Markdown。
                            """),
                    AiModelGateway.message("user", """
                            报告类型：%s
                            统计范围：近 %d 天
                            系统数据：
                            %s

                            请返回 JSON：
                            {
                              "title": "",
                              "summary": "",
                              "highlights": [],
                              "risks": [],
                              "recommendations": [],
                              "evidence": []
                            }
                            每个数组控制在 3 到 6 条，内容要能被科研人员直接复核。
                            """.formatted(reportType, days, context))
            ));
            GeneratedReport generated = new GeneratedReport(
                    firstNonBlank(text(node, "title"), defaultTitle(reportType, days)),
                    firstNonBlank(text(node, "summary"), fallbackSummary(summary, days)),
                    nonEmptyList(node.path("highlights"), fallbackHighlights(summary, trend, ecosystems)),
                    nonEmptyList(node.path("risks"), fallbackRisks(summary, protection)),
                    nonEmptyList(node.path("recommendations"), fallbackRecommendations()),
                    nonEmptyList(node.path("evidence"), fallbackEvidence(summary, days, ragHits))
            );
            return generated;
        } catch (RuntimeException ignored) {
            return new GeneratedReport(
                    defaultTitle(reportType, days),
                    fallbackSummary(summary, days),
                    fallbackHighlights(summary, trend, ecosystems),
                    fallbackRisks(summary, protection),
                    fallbackRecommendations(),
                    fallbackEvidence(summary, days, ragHits)
            );
        }
    }

    private String buildContext(
            DashboardSummary summary,
            List<NameValuePoint> trend,
            List<NameValuePoint> observers,
            List<EcosystemAnalyticsPoint> ecosystems,
            List<NameValuePoint> protection,
            List<RagSearchHit> ragHits
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("物种总数=" + summary.totalSpecies() + "，观测记录=" + summary.totalObservations()
                + "，生态系统=" + summary.totalEcosystems() + "，近7天观测=" + summary.recentObservationCount());
        lines.add("近期趋势=" + summarizeNameValues(trend, 8));
        lines.add("活跃人员=" + summarizeNameValues(observers, 5));
        lines.add("保护等级=" + summarizeNameValues(protection, 5));
        lines.add("生态系统=" + ecosystems.stream().limit(6)
                .map(item -> item.ecosystemName() + "(" + item.observationCount() + "次观测/" + item.speciesCount() + "种)")
                .toList());
        if (!ragHits.isEmpty()) {
            lines.add("RAG召回证据=" + ragHits.stream().limit(5)
                    .map(item -> item.title() + "：" + item.summary())
                    .toList());
        }
        return String.join("\n", lines);
    }

    private String summarizeNameValues(List<NameValuePoint> points, int limit) {
        if (points == null || points.isEmpty()) {
            return "暂无";
        }
        return points.stream().limit(limit).map(item -> item.name() + "=" + item.value()).toList().toString();
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

    private List<String> fallbackEvidence(DashboardSummary summary, int days, List<RagSearchHit> ragHits) {
        List<String> evidence = new ArrayList<>(List.of(
                "统计范围：近 " + days + " 天",
                "物种档案总数：" + summary.totalSpecies(),
                "观测记录总数：" + summary.totalObservations(),
                "生态系统总数：" + summary.totalEcosystems()
        ));
        ragHits.stream().limit(4)
                .map(item -> "RAG证据：" + item.title() + "（相似度 " + String.format(Locale.ROOT, "%.2f", item.score()) + "）")
                .forEach(evidence::add);
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
                report.getCreatedAt()
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

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    private List<String> nonEmptyList(JsonNode node, List<String> fallback) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (StringUtils.hasText(value)) {
                    values.add(value);
                }
            }
        }
        return values.isEmpty() ? fallback : values;
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
