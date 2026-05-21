package com.gsmv.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsmv.ai.dto.ObservationAiDtos;
import com.gsmv.ai.rag.RagKnowledgeService;
import com.gsmv.ai.rag.RagSearchHit;
import com.gsmv.audit.service.AuditService;
import com.gsmv.observation.ObservationService;
import com.gsmv.observation.dto.ObservationDetailView;
import com.gsmv.observation.dto.ObservationSpeciesView;
import com.gsmv.security.SecurityUtils;
import com.gsmv.species.SpeciesService;
import com.gsmv.species.dto.SpeciesDetailView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ObservationAiService {

    private final AiModelGateway aiModelGateway;
    private final SpeciesService speciesService;
    private final ObservationService observationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final RagKnowledgeService ragKnowledgeService;

    public ObservationAiService(
            AiModelGateway aiModelGateway,
            SpeciesService speciesService,
            ObservationService observationService,
            AuditService auditService,
            ObjectMapper objectMapper,
            RagKnowledgeService ragKnowledgeService
    ) {
        this.aiModelGateway = aiModelGateway;
        this.speciesService = speciesService;
        this.observationService = observationService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.ragKnowledgeService = ragKnowledgeService;
    }

    public ObservationAiDtos.AnalyzeObservationResponse analyze(ObservationAiDtos.AnalyzeObservationRequest request) {
        Set<String> tagSet = new LinkedHashSet<>(buildRuleTags(request));
        List<ObservationAiDtos.ObservationAnomaly> anomalies = buildAnomalies(request);

        JsonNode result = aiModelGateway.deepSeekJson(List.of(
                AiModelGateway.message("system", """
                        你是一名海洋观测记录分析助手。
                        请根据观测时间、地点、生态系统、环境参数和物种信息生成标签与简短提示。
                        返回内容必须是纯 JSON。
                        """),
                AiModelGateway.message("user", """
                        观测信息：
                        生态系统：%s
                        观测时间：%s
                        地点：%s
                        坐标：%s, %s
                        环境参数：%s
                        备注：%s
                        关联物种：%s
                        已检测到的异常提示：%s
                        RAG知识库参考：%s

                        请返回 JSON：
                        {
                          "summary": "",
                          "tags": [],
                          "reviewNotes": []
                        }
                        规则：
                        1. tags 限制在 3 到 6 个以内，使用简短中文标签。
                        2. reviewNotes 用于提醒用户人工核实，可为空数组。
                        3. 不要输出 Markdown。
                        """.formatted(
                        request.ecosystemName(),
                        request.observedAt(),
                        safe(request.locationName()),
                        request.locationLat(),
                        request.locationLng(),
                        environmentSummary(request.environment()),
                        safe(request.note()),
                        speciesSummary(request.speciesItems()),
                        anomalySummary(anomalies),
                        ragContext(request.ecosystemName() + " " + safe(request.locationName()) + " " + speciesSummary(request.speciesItems()))
                ))
        ));

        tagSet.addAll(stringList(result.path("tags")));
        List<String> reviewNotes = stringList(result.path("reviewNotes"));
        auditService.record(SecurityUtils.requireCurrentUser().userId(), "AI", "ANALYZE_OBSERVATION", "OBSERVATION", null, true,
                "{\"ecosystem\":\"" + escapeJson(request.ecosystemName()) + "\"}");

        return new ObservationAiDtos.AnalyzeObservationResponse(
                text(result, "summary"),
                List.copyOf(tagSet),
                reviewNotes,
                anomalies,
                !anomalies.isEmpty() || !reviewNotes.isEmpty()
        );
    }

    public ObservationAiDtos.QualityCheckResponse qualityCheck(Long observationId) {
        ObservationDetailView detail = observationService.getDetail(observationId);
        ObservationAiDtos.AnalyzeObservationRequest request = toAnalyzeRequest(detail);
        List<ObservationAiDtos.ObservationAnomaly> anomalies = buildAnomalies(request);
        List<ObservationAiDtos.QualityIssue> issues = new ArrayList<>();
        List<String> strengths = new ArrayList<>();
        int score = 100;

        if (!StringUtils.hasText(detail.locationName())) {
            score -= 6;
            issues.add(new ObservationAiDtos.QualityIssue(
                    "LOW",
                    "地点说明不完整",
                    "观测记录只有经纬度，缺少可读的地点说明。",
                    "补充海域、站位、样线或近岸参照点，方便后续复核。"
            ));
        } else {
            strengths.add("地点说明和坐标信息完整。");
        }

        if (detail.speciesItems() == null || detail.speciesItems().isEmpty()) {
            score -= 22;
            issues.add(new ObservationAiDtos.QualityIssue(
                    "HIGH",
                    "未关联观测物种",
                    "这条观测没有关联任何物种，难以支撑物种分布或生态系统统计。",
                    "至少关联一个现场确认或待确认的物种，并填写估算数量或行为。"
            ));
        } else {
            strengths.add("已关联 " + detail.speciesItems().size() + " 个物种，可进入分布和生态统计。");
        }

        ObservationAiDtos.EnvironmentSnapshot environment = request.environment();
        if (environment == null || isEnvironmentEmpty(environment)) {
            score -= 16;
            issues.add(new ObservationAiDtos.QualityIssue(
                    "MEDIUM",
                    "环境参数缺失",
                    "水温、盐度、pH、溶解氧等环境参数缺少记录。",
                    "补充关键环境参数后，AI 异常检测和生态系统分析会更可靠。"
            ));
        } else {
            strengths.add("已记录环境参数，可用于生态条件复核。");
            score -= addEnvironmentIssues(environment, issues);
        }

        if (!StringUtils.hasText(detail.note())) {
            score -= 8;
            issues.add(new ObservationAiDtos.QualityIssue(
                    "LOW",
                    "备注偏少",
                    "缺少现场背景、采样方式或照片依据等说明。",
                    "补充天气、样线、拍摄情况或人工确认依据。"
            ));
        } else {
            strengths.add("备注中已有现场补充信息。");
        }

        for (ObservationAiDtos.ObservationAnomaly anomaly : anomalies) {
            score -= "HIGH".equalsIgnoreCase(anomaly.severity()) ? 18 : 10;
            issues.add(new ObservationAiDtos.QualityIssue(
                    anomaly.severity(),
                    "物种分布冲突",
                    anomaly.message(),
                    anomaly.suggestion()
            ));
        }

        score = Math.max(0, Math.min(100, score));
        String grade = score >= 85 ? "HIGH" : score >= 70 ? "MEDIUM" : "LOW";
        if (strengths.isEmpty()) {
            strengths.add("基础观测记录已保存，可继续补充证据提高质量。");
        }
        String summary = switch (grade) {
            case "HIGH" -> "这条观测记录信息较完整，适合直接进入统计分析和地图展示。";
            case "MEDIUM" -> "这条观测记录基本可用，但仍建议补充关键字段或复核提示项。";
            default -> "这条观测记录存在明显缺口，建议先补充信息或发起人工核验。";
        };

        auditService.record(SecurityUtils.requireCurrentUser().userId(), "AI", "QUALITY_CHECK_OBSERVATION", "OBSERVATION", observationId, true,
                "{\"score\":" + score + "}");
        return new ObservationAiDtos.QualityCheckResponse(
                observationId,
                score,
                grade,
                summary,
                strengths,
                issues,
                score < 70 || issues.stream().anyMatch(issue -> "HIGH".equalsIgnoreCase(issue.severity()))
        );
    }

    private List<String> buildRuleTags(ObservationAiDtos.AnalyzeObservationRequest request) {
        List<String> tags = new ArrayList<>();
        if (StringUtils.hasText(request.ecosystemName())) {
            tags.add(request.ecosystemName().trim());
        }

        LocalDateTime observedAt = request.observedAt();
        int month = observedAt.getMonthValue();
        if (month >= 3 && month <= 5) {
            tags.add("春季观测");
        } else if (month >= 6 && month <= 8) {
            tags.add("夏季观测");
        } else if (month >= 9 && month <= 11) {
            tags.add("秋季观测");
        } else {
            tags.add("冬季观测");
        }

        int hour = observedAt.getHour();
        tags.add(hour >= 18 || hour < 6 ? "夜间观测" : "日间观测");

        ObservationAiDtos.EnvironmentSnapshot environment = request.environment();
        if (environment != null) {
            if (compare(environment.salinity(), 35) >= 0) {
                tags.add("高盐度环境");
            } else if (compare(environment.salinity(), 20) <= 0 && environment.salinity() != null) {
                tags.add("低盐度环境");
            }
            if (compare(environment.waterTemperature(), 28) >= 0) {
                tags.add("高温水域");
            } else if (compare(environment.waterTemperature(), 16) <= 0 && environment.waterTemperature() != null) {
                tags.add("低温水域");
            }
            if (compare(environment.dissolvedOxygen(), 5) < 0 && environment.dissolvedOxygen() != null) {
                tags.add("低溶氧提示");
            }
            if (compare(environment.depthMeters(), 30) >= 0) {
                tags.add("深水观测");
            } else if (compare(environment.depthMeters(), 5) <= 0 && environment.depthMeters() != null) {
                tags.add("浅水观测");
            }
        }

        List<ObservationAiDtos.SpeciesObservationItem> items = request.speciesItems() == null ? List.of() : request.speciesItems();
        if (items.size() > 1) {
            tags.add("多物种共现");
        }
        return tags;
    }

    private List<ObservationAiDtos.ObservationAnomaly> buildAnomalies(ObservationAiDtos.AnalyzeObservationRequest request) {
        List<ObservationAiDtos.ObservationAnomaly> anomalies = new ArrayList<>();
        List<ObservationAiDtos.SpeciesObservationItem> items = request.speciesItems() == null ? List.of() : request.speciesItems();
        for (ObservationAiDtos.SpeciesObservationItem item : items) {
            if (item == null || item.speciesId() == null) {
                continue;
            }
            SpeciesDetailView detail;
            try {
                detail = speciesService.getSpecies(item.speciesId());
            } catch (Exception ex) {
                continue;
            }

            if (detail.distributionLat() != null && detail.distributionLng() != null) {
                double distanceKm = haversine(
                        request.locationLat().doubleValue(),
                        request.locationLng().doubleValue(),
                        detail.distributionLat().doubleValue(),
                        detail.distributionLng().doubleValue()
                );
                if (distanceKm >= 2000) {
                    anomalies.add(new ObservationAiDtos.ObservationAnomaly(
                            "HIGH",
                            displaySpeciesName(detail.chineseName(), detail.scientificName()),
                            "观测点与档案分布点相距约 " + roundDistance(distanceKm) + " km，超出常规参考范围",
                            "建议核对定位、物种选择或补充人工复核说明",
                            distanceKm
                    ));
                } else if (distanceKm >= 800) {
                    anomalies.add(new ObservationAiDtos.ObservationAnomaly(
                            "MEDIUM",
                            displaySpeciesName(detail.chineseName(), detail.scientificName()),
                            "观测点与档案分布点相距约 " + roundDistance(distanceKm) + " km，建议确认是否属于迁移、扩散或误录",
                            "建议补充现场照片、行为描述或专家确认意见",
                            distanceKm
                    ));
                }
            }
        }
        return anomalies;
    }

    private ObservationAiDtos.AnalyzeObservationRequest toAnalyzeRequest(ObservationDetailView detail) {
        return new ObservationAiDtos.AnalyzeObservationRequest(
                detail.ecosystemId(),
                detail.ecosystemName(),
                detail.observedAt(),
                detail.locationLat(),
                detail.locationLng(),
                detail.locationName(),
                detail.note(),
                parseEnvironment(detail.envJson()),
                detail.speciesItems() == null ? List.of() : detail.speciesItems().stream().map(this::toAiSpeciesItem).toList()
        );
    }

    private ObservationAiDtos.SpeciesObservationItem toAiSpeciesItem(ObservationSpeciesView item) {
        return new ObservationAiDtos.SpeciesObservationItem(
                item.speciesId(),
                item.scientificName(),
                item.chineseName(),
                item.countEstimated(),
                item.behavior(),
                item.comment()
        );
    }

    private ObservationAiDtos.EnvironmentSnapshot parseEnvironment(String envJson) {
        if (!StringUtils.hasText(envJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(envJson, ObservationAiDtos.EnvironmentSnapshot.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isEnvironmentEmpty(ObservationAiDtos.EnvironmentSnapshot environment) {
        return environment.waterTemperature() == null
                && environment.salinity() == null
                && environment.ph() == null
                && environment.dissolvedOxygen() == null
                && environment.transparency() == null
                && environment.depthMeters() == null
                && !StringUtils.hasText(environment.weather())
                && !StringUtils.hasText(environment.seaState());
    }

    private int addEnvironmentIssues(ObservationAiDtos.EnvironmentSnapshot environment, List<ObservationAiDtos.QualityIssue> issues) {
        int penalty = 0;
        if (environment.waterTemperature() == null || environment.salinity() == null) {
            penalty += 8;
            issues.add(new ObservationAiDtos.QualityIssue(
                    "MEDIUM",
                    "核心水文参数不完整",
                    "水温和盐度是判断海洋观测环境的重要基础字段。",
                    "优先补齐水温和盐度，再补充 pH、溶解氧、透明度等参数。"
            ));
        }
        if (environment.ph() != null && (compare(environment.ph(), 6.5) < 0 || compare(environment.ph(), 9.0) > 0)) {
            penalty += 10;
            issues.add(new ObservationAiDtos.QualityIssue(
                    "HIGH",
                    "pH 数值异常",
                    "当前 pH 超出常见海水观测范围。",
                    "请核对仪器校准、单位和录入值。"
            ));
        }
        if (environment.dissolvedOxygen() != null && compare(environment.dissolvedOxygen(), 3) < 0) {
            penalty += 10;
            issues.add(new ObservationAiDtos.QualityIssue(
                    "HIGH",
                    "溶解氧偏低",
                    "溶解氧低于常规阈值，可能代表局部低氧环境或录入异常。",
                    "建议复核采样时间、深度和现场仪器读数。"
            ));
        }
        return penalty;
    }

    private String environmentSummary(ObservationAiDtos.EnvironmentSnapshot environment) {
        if (environment == null) {
            return "未填写";
        }
        List<String> entries = new ArrayList<>();
        addEntry(entries, "水温", environment.waterTemperature(), "°C");
        addEntry(entries, "盐度", environment.salinity(), "‰");
        addEntry(entries, "pH", environment.ph(), "");
        addEntry(entries, "溶解氧", environment.dissolvedOxygen(), "mg/L");
        addEntry(entries, "透明度", environment.transparency(), "m");
        addEntry(entries, "水深", environment.depthMeters(), "m");
        if (StringUtils.hasText(environment.weather())) {
            entries.add("天气 " + environment.weather().trim());
        }
        if (StringUtils.hasText(environment.seaState())) {
            entries.add("海况 " + environment.seaState().trim());
        }
        return entries.isEmpty() ? "未填写" : String.join("；", entries);
    }

    private void addEntry(List<String> entries, String label, BigDecimal value, String unit) {
        if (value != null) {
            entries.add(label + " " + value.stripTrailingZeros().toPlainString() + unit);
        }
    }

    private String speciesSummary(List<ObservationAiDtos.SpeciesObservationItem> items) {
        if (items == null || items.isEmpty()) {
            return "未关联物种";
        }
        List<String> values = new ArrayList<>();
        for (ObservationAiDtos.SpeciesObservationItem item : items) {
            if (item == null) {
                continue;
            }
            String name = firstNonBlank(item.chineseName(), item.scientificName(), item.speciesId() == null ? "" : "物种#" + item.speciesId());
            StringBuilder builder = new StringBuilder(name);
            if (item.countEstimated() != null) {
                builder.append("，约 ").append(item.countEstimated()).append(" 个体");
            }
            if (StringUtils.hasText(item.behavior())) {
                builder.append("，行为：").append(item.behavior().trim());
            }
            values.add(builder.toString());
        }
        return values.isEmpty() ? "未关联物种" : String.join("；", values);
    }

    private String anomalySummary(List<ObservationAiDtos.ObservationAnomaly> anomalies) {
        if (anomalies.isEmpty()) {
            return "暂未发现明显冲突";
        }
        List<String> values = new ArrayList<>();
        for (ObservationAiDtos.ObservationAnomaly anomaly : anomalies) {
            values.add(anomaly.speciesName() + "：" + anomaly.message());
        }
        return String.join("；", values);
    }

    private String ragContext(String query) {
        if (!StringUtils.hasText(query)) {
            return "无";
        }
        List<RagSearchHit> hits = ragKnowledgeService.retrieveForScenario(RagKnowledgeService.SCENARIO_OBSERVATION_ANALYSIS, query, 4);
        if (hits.isEmpty()) {
            return "无";
        }
        return hits.stream()
                .map(hit -> hit.title() + "：" + firstNonBlank(hit.summary(), hit.content()))
                .toList()
                .toString();
    }

    private int compare(BigDecimal value, double compareTo) {
        if (value == null) {
            return -1;
        }
        return value.compareTo(BigDecimal.valueOf(compareTo));
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371.0d;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private double roundDistance(double distanceKm) {
        return BigDecimal.valueOf(distanceKm).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private String displaySpeciesName(String chineseName, String scientificName) {
        if (StringUtils.hasText(chineseName) && StringUtils.hasText(scientificName)) {
            return chineseName.trim() + " / " + scientificName.trim();
        }
        return firstNonBlank(chineseName, scientificName, "未命名物种");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (!node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private String text(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() || fieldNode.isNull() ? "" : fieldNode.asText("").trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
