package com.gsmv.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.gsmv.ai.dto.ObservationAiDtos;
import com.gsmv.audit.service.AuditService;
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
    private final AuditService auditService;

    public ObservationAiService(AiModelGateway aiModelGateway, SpeciesService speciesService, AuditService auditService) {
        this.aiModelGateway = aiModelGateway;
        this.speciesService = speciesService;
        this.auditService = auditService;
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
                        anomalySummary(anomalies)
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
