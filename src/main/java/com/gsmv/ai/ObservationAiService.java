package com.gsmv.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsmv.ai.agent.AgentOrchestratorService;
import com.gsmv.ai.agent.AgentTask;
import com.gsmv.ai.agent.dto.AgentDtos;
import com.gsmv.ai.dto.ObservationAiDtos;
import com.gsmv.ai.rag.RagKnowledgeService;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ObservationAiService {

    private final SpeciesService speciesService;
    private final ObservationService observationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AgentOrchestratorService agentOrchestratorService;

    public ObservationAiService(
            SpeciesService speciesService,
            ObservationService observationService,
            AuditService auditService,
            ObjectMapper objectMapper,
            AgentOrchestratorService agentOrchestratorService
    ) {
        this.speciesService = speciesService;
        this.observationService = observationService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.agentOrchestratorService = agentOrchestratorService;
    }

    public ObservationAiDtos.AnalyzeObservationResponse analyze(ObservationAiDtos.AnalyzeObservationRequest request) {
        Set<String> tagSet = new LinkedHashSet<>(buildRuleTags(request));
        List<ObservationAiDtos.ObservationAnomaly> anomalies = buildAnomalies(request);
        List<String> reviewNotes = buildRuleReviewNotes(request, anomalies);
        String summary = buildRuleObservationSummary(request, anomalies);

        auditService.record(SecurityUtils.requireCurrentUser().userId(), "AI", "ANALYZE_OBSERVATION", "OBSERVATION", null, true,
                "{\"ecosystem\":\"" + escapeJson(request.ecosystemName()) + "\"}");

        ObservationAiDtos.AnalyzeObservationResponse response = new ObservationAiDtos.AnalyzeObservationResponse(
                summary,
                List.copyOf(tagSet),
                reviewNotes,
                anomalies,
                !anomalies.isEmpty() || !reviewNotes.isEmpty()
        );
        AgentDtos.AgentRunView run = agentOrchestratorService.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_OBSERVATION_QA,
                "OBSERVATION_DRAFT",
                null,
                firstNonBlank(request.ecosystemName(), request.locationName(), speciesSummary(request.speciesItems()), "observation draft"),
                RagKnowledgeService.SCENARIO_OBSERVATION_ANALYSIS,
                mapOf(
                        "summary", response.summary(),
                        "tags", response.tags(),
                        "reviewNotes", response.reviewNotes(),
                        "anomalies", response.anomalies(),
                        "needsReview", response.needsReview(),
                        "speciesItems", request.speciesItems(),
                        "ecosystemName", request.ecosystemName(),
                        "observedAt", request.observedAt(),
                        "locationLat", request.locationLat(),
                        "locationLng", request.locationLng(),
                        "locationName", request.locationName(),
                        "environment", request.environment(),
                        "note", request.note()
                ),
                List.of()
        ));
        return new ObservationAiDtos.AnalyzeObservationResponse(
                response.summary(),
                response.tags(),
                response.reviewNotes(),
                response.anomalies(),
                response.needsReview(),
                run.id(),
                run.steps(),
                run.verificationStatus(),
                run.confidence()
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
                    "Location description incomplete",
                    "The record only has coordinates and lacks a readable location description.",
                    "Add sea area, station, transect, or nearby reference point for later review."
            ));
        } else {
            strengths.add("Location description and coordinates are complete.");
        }

        if (detail.speciesItems() == null || detail.speciesItems().isEmpty()) {
            score -= 22;
            issues.add(new ObservationAiDtos.QualityIssue(
                    "HIGH",
                    "No observed species linked",
                    "The record has no species association, so distribution and ecosystem statistics are weak.",
                    "Link at least one confirmed or pending species and fill estimated count or behavior."
            ));
        } else {
            strengths.add("Linked " + detail.speciesItems().size() + " species for distribution and ecosystem statistics.");
        }

        ObservationAiDtos.EnvironmentSnapshot environment = request.environment();
        if (environment == null || isEnvironmentEmpty(environment)) {
            score -= 16;
            issues.add(new ObservationAiDtos.QualityIssue(
                    "MEDIUM",
                    "Environment parameters missing",
                    "Water temperature, salinity, pH, dissolved oxygen, or related parameters are missing.",
                    "Add key environment parameters so anomaly checks and ecosystem analysis are more reliable."
            ));
        } else {
            strengths.add("Environment parameters are recorded and can support ecological review.");
            score -= addEnvironmentIssues(environment, issues);
        }

        if (!StringUtils.hasText(detail.note())) {
            score -= 8;
            issues.add(new ObservationAiDtos.QualityIssue(
                    "LOW",
                    "Notes are sparse",
                    "The record lacks field background, sampling method, or photo evidence notes.",
                    "Add weather, transect, photo context, or manual confirmation evidence."
            ));
        } else {
            strengths.add("Field notes contain supplemental context.");
        }

        for (ObservationAiDtos.ObservationAnomaly anomaly : anomalies) {
            score -= "HIGH".equalsIgnoreCase(anomaly.severity()) ? 18 : 10;
            issues.add(new ObservationAiDtos.QualityIssue(
                    anomaly.severity(),
                    "Species distribution conflict",
                    anomaly.message(),
                    anomaly.suggestion()
            ));
        }

        score = Math.max(0, Math.min(100, score));
        String grade = score >= 85 ? "HIGH" : score >= 70 ? "MEDIUM" : "LOW";
        if (strengths.isEmpty()) {
            strengths.add("The base observation record is saved; more evidence can improve quality.");
        }
        String summary = switch (grade) {
            case "HIGH" -> "This observation is complete enough for statistics and map display.";
            case "MEDIUM" -> "This observation is usable, but key fields or review hints should be supplemented.";
            default -> "This observation has clear gaps; supplement information or start manual review first.";
        };

        auditService.record(SecurityUtils.requireCurrentUser().userId(), "AI", "QUALITY_CHECK_OBSERVATION", "OBSERVATION", observationId, true,
                "{\"score\":" + score + "}");
        ObservationAiDtos.QualityCheckResponse response = new ObservationAiDtos.QualityCheckResponse(
                observationId,
                score,
                grade,
                summary,
                strengths,
                issues,
                score < 70 || issues.stream().anyMatch(issue -> "HIGH".equalsIgnoreCase(issue.severity()))
        );
        AgentDtos.AgentRunView run = agentOrchestratorService.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_OBSERVATION_QA,
                "OBSERVATION",
                observationId,
                firstNonBlank(detail.locationName(), detail.ecosystemName(), "observation #" + observationId),
                RagKnowledgeService.SCENARIO_OBSERVATION_ANALYSIS,
                mapOf(
                        "score", response.score(),
                        "grade", response.grade(),
                        "summary", response.summary(),
                        "strengths", response.strengths(),
                        "issues", response.issues(),
                        "anomalies", anomalies,
                        "needsReview", response.needsReview(),
                        "speciesItems", request.speciesItems(),
                        "ecosystemName", request.ecosystemName(),
                        "observedAt", request.observedAt(),
                        "locationLat", request.locationLat(),
                        "locationLng", request.locationLng(),
                        "locationName", request.locationName(),
                        "environment", request.environment(),
                        "note", request.note()
                ),
                List.of()
        ));
        return new ObservationAiDtos.QualityCheckResponse(
                response.observationId(),
                response.score(),
                response.grade(),
                response.summary(),
                response.strengths(),
                response.issues(),
                response.needsReview(),
                run.id(),
                run.steps(),
                run.verificationStatus(),
                run.confidence()
        );
    }

    private String buildRuleObservationSummary(
            ObservationAiDtos.AnalyzeObservationRequest request,
            List<ObservationAiDtos.ObservationAnomaly> anomalies
    ) {
        int speciesCount = request.speciesItems() == null ? 0 : request.speciesItems().size();
        String location = firstNonBlank(request.locationName(), request.ecosystemName(), "unmarked location");
        String observedAt = request.observedAt() == null ? "unmarked time" : request.observedAt().toString();
        String anomalyText = anomalies == null || anomalies.isEmpty()
                ? "no obvious distribution or environment anomaly"
                : "found " + anomalies.size() + " review hint(s)";
        return "Rule and system context analyzed " + location + " at " + observedAt
                + "; \u5173\u8054\u7269\u79cd " + speciesCount + " \u4e2a; " + anomalyText + ".";
    }

    private List<String> buildRuleReviewNotes(
            ObservationAiDtos.AnalyzeObservationRequest request,
            List<ObservationAiDtos.ObservationAnomaly> anomalies
    ) {
        List<String> notes = new ArrayList<>();
        if (!StringUtils.hasText(request.locationName())) {
            notes.add("Add readable location, transect, or nearby reference point.");
        }
        if (request.speciesItems() == null || request.speciesItems().isEmpty()) {
            notes.add("Link at least one confirmed or pending species.");
        }
        if (request.environment() == null || isEnvironmentEmpty(request.environment())) {
            notes.add("Add water temperature, salinity, pH, dissolved oxygen, or depth.");
        }
        if (anomalies != null) {
            anomalies.stream()
                    .map(ObservationAiDtos.ObservationAnomaly::message)
                    .filter(StringUtils::hasText)
                    .limit(3)
                    .forEach(notes::add);
        }
        return notes.stream().distinct().limit(6).toList();
    }

    private List<String> buildRuleTags(ObservationAiDtos.AnalyzeObservationRequest request) {
        List<String> tags = new ArrayList<>();
        if (StringUtils.hasText(request.ecosystemName())) {
            tags.add(request.ecosystemName().trim());
        }

        LocalDateTime observedAt = request.observedAt();
        int month = observedAt.getMonthValue();
        if (month >= 3 && month <= 5) {
            tags.add("spring observation");
        } else if (month >= 6 && month <= 8) {
            tags.add("summer observation");
        } else if (month >= 9 && month <= 11) {
            tags.add("autumn observation");
        } else {
            tags.add("winter observation");
        }

        int hour = observedAt.getHour();
        tags.add(hour >= 18 || hour < 6 ? "night observation" : "day observation");

        ObservationAiDtos.EnvironmentSnapshot environment = request.environment();
        if (environment != null) {
            if (compare(environment.salinity(), 35) >= 0) {
                tags.add("high salinity");
            } else if (compare(environment.salinity(), 20) <= 0 && environment.salinity() != null) {
                tags.add("low salinity");
            }
            if (compare(environment.waterTemperature(), 28) >= 0) {
                tags.add("warm water");
            } else if (compare(environment.waterTemperature(), 16) <= 0 && environment.waterTemperature() != null) {
                tags.add("cold water");
            }
            if (compare(environment.dissolvedOxygen(), 5) < 0 && environment.dissolvedOxygen() != null) {
                tags.add("low dissolved oxygen");
            }
            if (compare(environment.depthMeters(), 30) >= 0) {
                tags.add("deep water");
            } else if (compare(environment.depthMeters(), 5) <= 0 && environment.depthMeters() != null) {
                tags.add("shallow water");
            }
        }

        List<ObservationAiDtos.SpeciesObservationItem> items = request.speciesItems() == null ? List.of() : request.speciesItems();
        if (items.size() > 1) {
            tags.add("multi species co-occurrence");
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
                            "Observation point is about " + roundDistance(distanceKm) + " km from the species distribution reference.",
                            "Check coordinates, species selection, or add manual review notes.",
                            distanceKm
                    ));
                } else if (distanceKm >= 800) {
                    anomalies.add(new ObservationAiDtos.ObservationAnomaly(
                            "MEDIUM",
                            displaySpeciesName(detail.chineseName(), detail.scientificName()),
                            "Observation point is about " + roundDistance(distanceKm) + " km from the species distribution reference.",
                            "Add field photos, behavior description, or expert confirmation.",
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
                    "Core hydrology parameters incomplete",
                    "Water temperature and salinity are key baseline fields for marine observations.",
                    "Fill water temperature and salinity first, then pH, dissolved oxygen, and transparency."
            ));
        }
        if (environment.ph() != null && (compare(environment.ph(), 6.5) < 0 || compare(environment.ph(), 9.0) > 0)) {
            penalty += 10;
            issues.add(new ObservationAiDtos.QualityIssue(
                    "HIGH",
                    "pH value abnormal",
                    "The current pH is outside the usual seawater observation range.",
                    "Check instrument calibration, unit, and entered value."
            ));
        }
        if (environment.dissolvedOxygen() != null && compare(environment.dissolvedOxygen(), 3) < 0) {
            penalty += 10;
            issues.add(new ObservationAiDtos.QualityIssue(
                    "HIGH",
                    "Low dissolved oxygen",
                    "Dissolved oxygen is below the regular threshold and may indicate local hypoxia or data entry error.",
                    "Review sampling time, depth, and field instrument readings."
            ));
        }
        return penalty;
    }

    private String speciesSummary(List<ObservationAiDtos.SpeciesObservationItem> items) {
        if (items == null || items.isEmpty()) {
            return "no linked species";
        }
        List<String> values = new ArrayList<>();
        for (ObservationAiDtos.SpeciesObservationItem item : items) {
            if (item == null) {
                continue;
            }
            String name = firstNonBlank(item.chineseName(), item.scientificName(), item.speciesId() == null ? "" : "species#" + item.speciesId());
            StringBuilder builder = new StringBuilder(name);
            if (item.countEstimated() != null) {
                builder.append(", about ").append(item.countEstimated()).append(" individuals");
            }
            if (StringUtils.hasText(item.behavior())) {
                builder.append(", behavior: ").append(item.behavior().trim());
            }
            values.add(builder.toString());
        }
        return values.isEmpty() ? "no linked species" : String.join("; ", values);
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
        return firstNonBlank(chineseName, scientificName, "unnamed species");
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
}
