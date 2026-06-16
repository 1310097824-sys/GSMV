package com.gsmv.ai;

import com.gsmv.ai.agent.AgentOrchestratorService;
import com.gsmv.ai.agent.AgentTask;
import com.gsmv.ai.agent.dto.AgentDtos;
import com.gsmv.ai.dto.SpeciesAiDtos;
import com.gsmv.ai.rag.RagKnowledgeService;
import com.gsmv.ai.rag.dto.RagDtos;
import com.gsmv.audit.service.AuditService;
import com.gsmv.common.ErrorCode;
import com.gsmv.common.PageResponse;
import com.gsmv.common.exception.BusinessException;
import com.gsmv.security.SecurityUtils;
import com.gsmv.species.SpeciesService;
import com.gsmv.species.dto.SpeciesView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SpeciesAiService {

    private final AiProperties aiProperties;
    private final SpeciesService speciesService;
    private final AuditService auditService;
    private final AgentOrchestratorService agentOrchestratorService;

    public SpeciesAiService(
            AiProperties aiProperties,
            SpeciesService speciesService,
            AuditService auditService,
            AgentOrchestratorService agentOrchestratorService
    ) {
        this.aiProperties = aiProperties;
        this.speciesService = speciesService;
        this.auditService = auditService;
        this.agentOrchestratorService = agentOrchestratorService;
    }

    public SpeciesAiDtos.IdentifyImageResponse identifyImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先上传海洋生物图片", HttpStatus.BAD_REQUEST);
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持图片文件用于智能识别", HttpStatus.BAD_REQUEST);
        }

        try {
            byte[] imageBytes = file.getBytes();
            String agentPrompt = firstNonBlank(file.getOriginalFilename(), "marine species image identification");
            Map<String, Object> agentInput = mapOf(
                    "fileName", file.getOriginalFilename(),
                    "contentType", file.getContentType(),
                    "lowConfidenceThreshold", aiProperties.lowConfidenceThreshold(),
                    "ragEvidencePending", true,
                    "ragEvidence", List.of(),
                    "conflictWarnings", List.of()
            );
            AgentDtos.AgentRunView run = agentOrchestratorService.execute(new AgentTask(
                    AgentOrchestratorService.WORKFLOW_SPECIES_IDENTIFY,
                    "IMAGE",
                    null,
                    agentPrompt,
                    RagKnowledgeService.SCENARIO_IMAGE_IDENTIFICATION,
                    agentInput,
                    List.of(),
                    mapOf(
                            "imageBytes", imageBytes,
                            "contentType", file.getContentType()
                    )
            ));
            AgentVisionResult visionResult = extractAgentVisionResult(run);
            List<SpeciesAiDtos.RelatedSpeciesRecord> relatedSpeciesRecords = searchRelatedSpecies(visionResult.keywords().toArray(String[]::new));
            AgentSpeciesDecision agentDecision = buildAgentSpeciesDecision(run, visionResult.confidence(), visionResult.needsHumanReview());

            auditService.record(
                    SecurityUtils.requireCurrentUser().userId(),
                    "AI",
                    "IDENTIFY_SPECIES",
                    "IMAGE",
                    null,
                    true,
                    "{\"file\":\"" + escapeJson(file.getOriginalFilename()) + "\"}"
            );

            return new SpeciesAiDtos.IdentifyImageResponse(
                    visionResult.likelyChineseName(),
                    visionResult.likelyScientificName(),
                    agentDecision.confidence(),
                    agentDecision.needsHumanReview(),
                    agentDecision.confidenceLabel(),
                    visionResult.reasoning(),
                    visionResult.candidates(),
                    relatedSpeciesRecords,
                    agentDecision.ragEvidence(),
                    agentDecision.confidenceAdjustedByRag(),
                    agentDecision.ragConclusion(),
                    agentDecision.conflictWarnings(),
                    run.id(),
                    run.steps(),
                    run.verificationStatus()
            );
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取识别图片失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public SpeciesAiDtos.AutocompleteResponse autocomplete(SpeciesAiDtos.AutocompleteRequest request) {
        if (!StringUtils.hasText(request.chineseName()) && !StringUtils.hasText(request.scientificName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少填写中文名或学名后再使用 AI 补全", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> agentInput = speciesProfileInput(request);
        String prompt = firstNonBlank(request.chineseName(), request.scientificName(), request.description(), "species profile autocomplete");
        AgentDtos.AgentRunView run = agentOrchestratorService.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_SPECIES_PROFILE_ASSIST,
                "SPECIES_PROFILE",
                null,
                prompt,
                RagKnowledgeService.SCENARIO_SPECIES_PROFILE,
                agentInput,
                List.of()
        ));
        Map<String, Object> profile = agentStepOutput(run, "Taxonomy Agent");
        List<SpeciesAiDtos.RelatedSpeciesRecord> related = searchRelatedSpecies(request.chineseName(), request.scientificName(), stringField(profile, "chineseName"), stringField(profile, "scientificName"));
        auditService.record(SecurityUtils.requireCurrentUser().userId(), "AI", "AUTOCOMPLETE_SPECIES", "SPECIES", null, true,
                "{\"chineseName\":\"" + escapeJson(request.chineseName()) + "\",\"scientificName\":\"" + escapeJson(request.scientificName()) + "\"}");
        return new SpeciesAiDtos.AutocompleteResponse(
                fallback(stringField(profile, "chineseName"), request.chineseName()),
                fallback(stringField(profile, "scientificName"), request.scientificName()),
                stringField(profile, "phylumName"),
                stringField(profile, "className"),
                stringField(profile, "orderName"),
                stringField(profile, "familyName"),
                stringField(profile, "genusName"),
                stringField(profile, "protectionLevel"),
                stringField(profile, "iucnStatus"),
                fallback(stringField(profile, "description"), request.description()),
                fallback(stringField(profile, "morphology"), request.morphology()),
                fallback(stringField(profile, "habit"), request.habit()),
                fallback(stringField(profile, "habitat"), request.habitat()),
                fallback(stringField(profile, "distribution"), request.distribution()),
                fallback(stringField(profile, "geoRangeText"), request.geoRangeText()),
                stringField(profile, "summary"),
                boundedConfidence(doubleField(profile, "confidence")),
                stringList(fieldValue(profile, "notes")),
                related,
                run.id(),
                run.steps(),
                run.verificationStatus()
        );
    }

    public SpeciesAiDtos.PolishTextResponse polishText(SpeciesAiDtos.PolishTextRequest request) {
        Map<String, Object> agentInput = mapOf(
                "assistType", "POLISH_TEXT",
                "fieldName", request.fieldName(),
                "text", request.text()
        );
        AgentDtos.AgentRunView run = agentOrchestratorService.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_SPECIES_PROFILE_ASSIST,
                "SPECIES_TEXT",
                null,
                firstNonBlank(request.text(), request.fieldName(), "species text polish"),
                RagKnowledgeService.SCENARIO_SPECIES_PROFILE,
                agentInput,
                List.of()
        ));
        Map<String, Object> output = agentStepOutput(run, "Taxonomy Agent");

        auditService.record(SecurityUtils.requireCurrentUser().userId(), "AI", "POLISH_SPECIES_TEXT", "SPECIES", null, true,
                "{\"field\":\"" + escapeJson(request.fieldName()) + "\"}");
        return new SpeciesAiDtos.PolishTextResponse(
                request.fieldName(),
                fallback(stringField(output, "polishedText"), request.text()),
                stringField(output, "summary"),
                stringList(fieldValue(output, "keywords")),
                run.id(),
                run.steps(),
                run.verificationStatus(),
                run.confidence()
        );
    }

    public SpeciesAiDtos.TranslateSpeciesResponse translate(SpeciesAiDtos.TranslateSpeciesRequest request) {
        Map<String, Object> agentInput = mapOf(
                "assistType", "TRANSLATE_SPECIES",
                "chineseName", request.chineseName(),
                "scientificName", request.scientificName(),
                "description", request.description(),
                "morphology", request.morphology(),
                "habit", request.habit(),
                "habitat", request.habitat(),
                "distribution", request.distribution(),
                "geoRangeText", request.geoRangeText(),
                "targetLanguage", request.targetLanguage()
        );
        AgentDtos.AgentRunView run = agentOrchestratorService.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_SPECIES_PROFILE_ASSIST,
                "SPECIES_TRANSLATION",
                null,
                firstNonBlank(request.chineseName(), request.scientificName(), request.description(), request.targetLanguage(), "species translation"),
                RagKnowledgeService.SCENARIO_SPECIES_PROFILE,
                agentInput,
                List.of()
        ));
        Map<String, Object> output = agentStepOutput(run, "Taxonomy Agent");

        auditService.record(SecurityUtils.requireCurrentUser().userId(), "AI", "TRANSLATE_SPECIES", "SPECIES", null, true,
                "{\"targetLanguage\":\"" + escapeJson(request.targetLanguage()) + "\"}");
        return new SpeciesAiDtos.TranslateSpeciesResponse(
                request.targetLanguage(),
                stringField(output, "description"),
                stringField(output, "morphology"),
                stringField(output, "habit"),
                stringField(output, "habitat"),
                stringField(output, "distribution"),
                stringField(output, "geoRangeText"),
                stringField(output, "summary"),
                run.id(),
                run.steps(),
                run.verificationStatus(),
                run.confidence()
        );
    }

    private Map<String, Object> speciesProfileInput(SpeciesAiDtos.AutocompleteRequest request) {
        return mapOf(
                "chineseName", request.chineseName(),
                "scientificName", request.scientificName(),
                "description", request.description(),
                "morphology", request.morphology(),
                "habit", request.habit(),
                "habitat", request.habitat(),
                "distribution", request.distribution(),
                "geoRangeText", request.geoRangeText()
        );
    }

    private AgentVisionResult extractAgentVisionResult(AgentDtos.AgentRunView run) {
        Map<String, Object> output = agentStepOutput(run, "Vision Review Agent");
        List<SpeciesAiDtos.IdentificationCandidate> candidates = parseAgentCandidates(fieldValue(output, "candidates"));
        if (candidates.isEmpty()) {
            candidates = parseAgentCandidates(fieldValue(output, "candidateSummaries"));
        }
        SpeciesAiDtos.IdentificationCandidate topCandidate = candidates.isEmpty() ? null : candidates.get(0);
        String likelyChineseName = firstNonBlank(
                stringField(output, "likelyChineseName"),
                topCandidate == null ? "" : topCandidate.chineseName()
        );
        String likelyScientificName = firstNonBlank(
                stringField(output, "likelyScientificName"),
                topCandidate == null ? "" : topCandidate.scientificName()
        );
        double confidence = boundedConfidence(doubleField(output, "confidence"));
        boolean needsHumanReview = booleanField(output, "needsHumanReview");
        List<String> keywords = new ArrayList<>();
        keywords.add(likelyChineseName);
        keywords.add(likelyScientificName);
        candidates.forEach(candidate -> {
            keywords.add(candidate.chineseName());
            keywords.add(candidate.scientificName());
        });
        return new AgentVisionResult(
                likelyChineseName,
                likelyScientificName,
                confidence,
                needsHumanReview,
                stringField(output, "reasoning"),
                candidates,
                keywords.stream().filter(StringUtils::hasText).distinct().toList()
        );
    }

    private Map<String, Object> agentStepOutput(AgentDtos.AgentRunView run, String agentName) {
        for (AgentDtos.AgentStepView step : run.steps()) {
            if (agentName.equals(step.agentName()) && step.output() instanceof Map<?, ?> map) {
                Map<String, Object> output = new LinkedHashMap<>();
                map.forEach((key, value) -> {
                    if (key != null) {
                        output.put(String.valueOf(key), value);
                    }
                });
                return output;
            }
        }
        return Map.of();
    }

    private List<SpeciesAiDtos.IdentificationCandidate> parseAgentCandidates(Object value) {
        List<SpeciesAiDtos.IdentificationCandidate> candidates = new ArrayList<>();
        for (Object item : objectItems(value)) {
            SpeciesAiDtos.IdentificationCandidate candidate = new SpeciesAiDtos.IdentificationCandidate(
                    stringField(item, "chineseName"),
                    stringField(item, "scientificName"),
                    boundedConfidence(doubleField(item, "confidence")),
                    stringField(item, "reason")
            );
            if (StringUtils.hasText(candidate.chineseName()) || StringUtils.hasText(candidate.scientificName())) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    private AgentSpeciesDecision buildAgentSpeciesDecision(
            AgentDtos.AgentRunView run,
            double modelConfidence,
            boolean modelNeedsHumanReview
    ) {
        double confidence = boundedConfidence(run.confidence() == null ? modelConfidence : run.confidence());
        List<RagDtos.RagEvidenceItem> ragEvidence = extractAgentRagEvidence(run);
        List<String> conflictWarnings = extractAgentConflictWarnings(run);
        boolean agentNeedsReview = "NEEDS_REVIEW".equalsIgnoreCase(run.verificationStatus())
                || "INSUFFICIENT_EVIDENCE".equalsIgnoreCase(run.verificationStatus());
        boolean needsHumanReview = modelNeedsHumanReview
                || agentNeedsReview
                || confidence < aiProperties.lowConfidenceThreshold()
                || !conflictWarnings.isEmpty();
        boolean confidenceAdjustedByRag = !ragEvidence.isEmpty() && Math.abs(confidence - modelConfidence) > 0.0001d;
        String ragConclusion = ragEvidence.isEmpty()
                ? "Agent RAG Evidence Agent did not retrieve supporting evidence."
                : "Agent RAG Evidence Agent retrieved " + ragEvidence.size() + " evidence item(s) for candidate verification.";
        return new AgentSpeciesDecision(
                confidence,
                needsHumanReview,
                needsHumanReview ? "Agent review required" : "Agent verified",
                ragEvidence,
                confidenceAdjustedByRag,
                ragConclusion,
                conflictWarnings
        );
    }

    private record AgentVisionResult(
            String likelyChineseName,
            String likelyScientificName,
            double confidence,
            boolean needsHumanReview,
            String reasoning,
            List<SpeciesAiDtos.IdentificationCandidate> candidates,
            List<String> keywords
    ) {
    }

    private List<RagDtos.RagEvidenceItem> extractAgentRagEvidence(AgentDtos.AgentRunView run) {
        List<RagDtos.RagEvidenceItem> evidence = new ArrayList<>();
        for (AgentDtos.AgentStepView step : run.steps()) {
            if (!"RAG Evidence Agent".equals(step.agentName())) {
                continue;
            }
            for (Object item : objectItems(step.evidence())) {
                RagDtos.RagEvidenceItem evidenceItem = toRagEvidenceItem(item);
                if (evidenceItem != null) {
                    evidence.add(evidenceItem);
                }
            }
        }
        return evidence;
    }

    private RagDtos.RagEvidenceItem toRagEvidenceItem(Object value) {
        if (value instanceof RagDtos.RagEvidenceItem item) {
            return item;
        }
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        return new RagDtos.RagEvidenceItem(
                stringField(value, "sourceType"),
                longField(value, "sourceId"),
                longField(value, "documentId"),
                longField(value, "chunkId"),
                stringField(value, "title"),
                stringField(value, "summary"),
                firstNonBlank(stringField(value, "contentSnippet"), stringField(value, "summary")),
                doubleField(value, "score"),
                stringField(value, "sourcePath"),
                stringField(value, "sourceName"),
                RagKnowledgeService.SCENARIO_IMAGE_IDENTIFICATION
        );
    }

    private List<String> extractAgentConflictWarnings(AgentDtos.AgentRunView run) {
        Set<String> warnings = new LinkedHashSet<>();
        Object finalOutput = run.finalOutput();
        addAgentWarnings(warnings, fieldValue(finalOutput, "riskFindings"));
        Object verifierOutput = fieldValue(finalOutput, "verifierOutput");
        addAgentWarnings(warnings, fieldValue(verifierOutput, "reviewFindings"));
        return new ArrayList<>(warnings);
    }

    private void addAgentWarnings(Set<String> warnings, Object value) {
        for (Object item : objectItems(value)) {
            String warning = firstNonBlank(
                    stringField(item, "message"),
                    stringField(item, "title"),
                    stringField(item, "summary"),
                    stringField(item, "description"),
                    item instanceof String text ? text : ""
            );
            if (StringUtils.hasText(warning)) {
                warnings.add(warning);
            }
        }
    }

    private List<Object> objectItems(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> values = new ArrayList<>();
            iterable.forEach(values::add);
            return values;
        }
        return List.of();
    }

    private Object fieldValue(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }

    private String stringField(Object value, String key) {
        Object field = fieldValue(value, key);
        return field == null ? "" : String.valueOf(field).trim();
    }

    private Long longField(Object value, String key) {
        Object field = fieldValue(value, key);
        if (field instanceof Number number) {
            return number.longValue();
        }
        try {
            return StringUtils.hasText(String.valueOf(field)) ? Long.parseLong(String.valueOf(field)) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private double doubleField(Object value, String key) {
        Object field = fieldValue(value, key);
        if (field instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return StringUtils.hasText(String.valueOf(field)) ? Double.parseDouble(String.valueOf(field)) : 0.0d;
        } catch (NumberFormatException ex) {
            return 0.0d;
        }
    }

    private boolean booleanField(Object value, String key) {
        Object field = fieldValue(value, key);
        if (field instanceof Boolean bool) {
            return bool;
        }
        if (field instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }

    private record AgentSpeciesDecision(
            double confidence,
            boolean needsHumanReview,
            String confidenceLabel,
            List<RagDtos.RagEvidenceItem> ragEvidence,
            boolean confidenceAdjustedByRag,
            String ragConclusion,
            List<String> conflictWarnings
    ) {
    }

    private List<SpeciesAiDtos.RelatedSpeciesRecord> searchRelatedSpecies(String... keywordGroups) {
        Set<String> normalizedKeywords = new LinkedHashSet<>();
        for (String keywordGroup : keywordGroups) {
            if (!StringUtils.hasText(keywordGroup)) {
                continue;
            }
            String trimmed = keywordGroup.trim();
            if (trimmed.contains(",")) {
                for (String item : trimmed.split(",")) {
                    if (StringUtils.hasText(item)) {
                        normalizedKeywords.add(item.trim());
                    }
                }
            } else {
                normalizedKeywords.add(trimmed);
            }
        }

        Map<Long, SpeciesAiDtos.RelatedSpeciesRecord> results = new LinkedHashMap<>();
        for (String keyword : normalizedKeywords) {
            PageResponse<SpeciesView> page = speciesService.listSpecies(keyword, 1, null, null, null, null, 1, 5);
            for (SpeciesView item : page.items()) {
                results.putIfAbsent(item.id(), new SpeciesAiDtos.RelatedSpeciesRecord(
                        item.id(),
                        item.chineseName(),
                        item.scientificName(),
                        item.classificationPath(),
                        item.protectionLevel(),
                        item.iucnStatus()
                ));
            }
            if (results.size() >= 8) {
                break;
            }
        }
        return new ArrayList<>(results.values());
    }

    private List<String> stringList(Object value) {
        List<String> values = new ArrayList<>();
        for (Object item : objectItems(value)) {
            String text = item == null ? "" : String.valueOf(item).trim();
            if (StringUtils.hasText(text)) {
                values.add(text);
            }
        }
        return values;
    }

    private double boundedConfidence(double value) {
        if (Double.isNaN(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private String fallback(String preferred, String fallbackValue) {
        return StringUtils.hasText(preferred) ? preferred : safe(fallbackValue);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
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
