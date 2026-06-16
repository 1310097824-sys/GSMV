package com.gsmv.ai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsmv.ai.AiModelGateway;
import com.gsmv.ai.agent.dto.AgentDtos;
import com.gsmv.ai.agent.mapper.AgentRunMapper;
import com.gsmv.ai.agent.model.AgentRun;
import com.gsmv.ai.agent.model.AgentStep;
import com.gsmv.ai.rag.RagKnowledgeService;
import com.gsmv.ai.rag.RagSearchHit;
import com.gsmv.common.ErrorCode;
import com.gsmv.common.PageResponse;
import com.gsmv.common.exception.BusinessException;
import com.gsmv.common.exception.NotFoundException;
import com.gsmv.ecosystem.mapper.EcosystemMapper;
import com.gsmv.ecosystem.model.Ecosystem;
import com.gsmv.observation.dto.ObservationSpeciesView;
import com.gsmv.observation.dto.ObservationView;
import com.gsmv.observation.mapper.ObservationMapper;
import com.gsmv.report.ReportService;
import com.gsmv.report.dto.DashboardSummary;
import com.gsmv.report.dto.EcosystemAnalyticsPoint;
import com.gsmv.report.dto.NameValuePoint;
import com.gsmv.security.CurrentUser;
import com.gsmv.security.SecurityUtils;
import com.gsmv.species.dto.SpeciesRow;
import com.gsmv.species.mapper.SpeciesMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AgentOrchestratorService {

    public static final String WORKFLOW_ASSISTANT_CHAT = "ASSISTANT_CHAT";
    public static final String WORKFLOW_SPECIES_IDENTIFY = "SPECIES_IDENTIFY";
    public static final String WORKFLOW_SPECIES_PROFILE_ASSIST = "SPECIES_PROFILE_ASSIST";
    public static final String WORKFLOW_OBSERVATION_QA = "OBSERVATION_QA";
    public static final String WORKFLOW_RESEARCH_REPORT = "RESEARCH_REPORT";
    public static final String WORKFLOW_KNOWLEDGE_GOVERNANCE = "KNOWLEDGE_GOVERNANCE";

    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_INSUFFICIENT_EVIDENCE = "INSUFFICIENT_EVIDENCE";
    public static final String STATUS_NEEDS_REVIEW = "NEEDS_REVIEW";
    private static final String VISION_REVIEW_SYSTEM_PROMPT = """
            You are a marine species image identification and review agent.
            Identify the most likely species candidates from the image and return JSON only.
            """;
    private static final String VISION_REVIEW_USER_PROMPT = """
            Return JSON in this shape:
            {
              "likelyChineseName": "",
              "likelyScientificName": "",
              "confidence": 0.0,
              "reasoning": "",
              "candidates": [
                {"chineseName": "", "scientificName": "", "confidence": 0.0, "reason": ""}
              ]
            }
            Rules:
            1. confidence must be between 0 and 1.
            2. Return 1 to 5 candidates sorted by confidence.
            3. If the image is unclear, lower confidence and explain uncertainty in reasoning.
            4. Output valid JSON only; do not output Markdown.
            """;
    private static final String SPECIES_PROFILE_ASSIST_SYSTEM_PROMPT = """
            You are a marine taxonomy and biodiversity archive assistant.
            Complete a species profile only from user-provided facts and retrieved evidence.
            Return JSON only.
            """;

    private final AgentRunMapper agentRunMapper;
    private final ObjectMapper objectMapper;
    private final RagKnowledgeService ragKnowledgeService;
    private final ReportService reportService;
    private final SpeciesMapper speciesMapper;
    private final ObservationMapper observationMapper;
    private final EcosystemMapper ecosystemMapper;
    private final AiModelGateway aiModelGateway;

    public AgentOrchestratorService(
            AgentRunMapper agentRunMapper,
            ObjectMapper objectMapper,
            RagKnowledgeService ragKnowledgeService,
            ReportService reportService,
            SpeciesMapper speciesMapper,
            ObservationMapper observationMapper,
            EcosystemMapper ecosystemMapper,
            AiModelGateway aiModelGateway
    ) {
        this.agentRunMapper = agentRunMapper;
        this.objectMapper = objectMapper;
        this.ragKnowledgeService = ragKnowledgeService;
        this.reportService = reportService;
        this.speciesMapper = speciesMapper;
        this.observationMapper = observationMapper;
        this.ecosystemMapper = ecosystemMapper;
        this.aiModelGateway = aiModelGateway;
    }

    @Transactional
    public AgentDtos.AgentRunView execute(AgentTask task) {
        CurrentUser currentUser = SecurityUtils.getCurrentUser().orElse(null);
        AgentRun run = new AgentRun();
        run.setWorkflowType(firstNonBlank(task.workflowType(), WORKFLOW_ASSISTANT_CHAT));
        run.setStatus("RUNNING");
        run.setSubjectType(normalizeNullable(task.subjectType()));
        run.setSubjectId(task.subjectId());
        run.setUserId(currentUser == null ? null : currentUser.userId());
        run.setPrompt(truncate(task.prompt(), 4000));
        run.setStartedAt(LocalDateTime.now());
        agentRunMapper.insertRun(run);

        List<AgentResult> results = new ArrayList<>();
        Map<String, Object> memory = new LinkedHashMap<>();
        List<AgentDefinition> chain = buildChain(task);

        int stepOrder = 1;
        boolean hasFailure = false;
        for (AgentDefinition agent : chain) {
            AgentResult result = executeStep(run.getId(), stepOrder, agent, task, memory, results);
            results.add(result);
            memory.put(agent.name(), result.output());
            if (!"SUCCESS".equals(result.status())) {
                hasFailure = true;
            }
            stepOrder++;
        }

        AgentResult finalResult = results.isEmpty()
                ? AgentResult.failure("Verifier Agent", "验证", "没有可执行的 agent")
                : results.get(results.size() - 1);
        String verificationStatus = firstNonBlank(finalResult.verificationStatus(), hasFailure ? STATUS_NEEDS_REVIEW : STATUS_VERIFIED);
        double confidence = boundedConfidence(finalResult.confidence() == null ? aggregateConfidence(results) : finalResult.confidence());
        String status = hasFailure ? "PARTIAL" : "SUCCESS";
        String summary = finalRunSummary(task, finalResult, results);
        Map<String, Object> finalOutput = buildFinalOutput(
                task,
                run.getWorkflowType(),
                status,
                verificationStatus,
                confidence,
                summary,
                results,
                finalResult
        );

        agentRunMapper.finishRun(
                run.getId(),
                status,
                summary,
                verificationStatus,
                BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP),
                writeJson(finalOutput),
                LocalDateTime.now()
        );
        return getRunSnapshot(run.getId());
    }

    @Transactional
    public void attachSubject(Long runId, String subjectType, Long subjectId) {
        if (runId != null && StringUtils.hasText(subjectType) && subjectId != null) {
            agentRunMapper.updateSubject(runId, subjectType.trim(), subjectId);
        }
    }

    public PageResponse<AgentDtos.AgentRunView> listRuns(
            String workflowType,
            String status,
            String verificationStatus,
            String keyword,
            int page,
            int size
    ) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        Long userFilter = canViewAllRuns(currentUser) ? null : currentUser.userId();
        String normalizedWorkflow = normalizeNullable(workflowType == null ? null : workflowType.toUpperCase(Locale.ROOT));
        String normalizedStatus = normalizeNullable(status == null ? null : status.toUpperCase(Locale.ROOT));
        String normalizedVerificationStatus = normalizeNullable(verificationStatus == null ? null : verificationStatus.toUpperCase(Locale.ROOT));
        String normalizedKeyword = normalizeNullable(keyword);
        List<AgentDtos.AgentRunView> items = agentRunMapper.findPage(
                        userFilter,
                        normalizedWorkflow,
                        normalizedStatus,
                        normalizedVerificationStatus,
                        normalizedKeyword,
                        safeSize,
                        offset
                ).stream()
                .map(run -> toRunView(run, List.of()))
                .toList();
        long total = agentRunMapper.count(
                userFilter,
                normalizedWorkflow,
                normalizedStatus,
                normalizedVerificationStatus,
                normalizedKeyword
        );
        return new PageResponse<>(items, total, safePage, safeSize);
    }

    public AgentDtos.AgentRunView getRun(Long id) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        AgentRun run = requireRun(id);
        if (!canViewAllRuns(currentUser) && !Objects.equals(run.getUserId(), currentUser.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "你只能查看自己发起的 Agent 协作轨迹", HttpStatus.FORBIDDEN);
        }
        return toRunView(run, agentRunMapper.findStepsByRunId(id));
    }

    public AgentDtos.AgentRunReplayView getRunReplay(Long id) {
        return buildReplayView(getRun(id));
    }

    public AgentDtos.AgentRunView getRunSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        AgentRun run = agentRunMapper.findRunById(id);
        return run == null ? null : toRunView(run, agentRunMapper.findStepsByRunId(id));
    }

    public AgentDtos.AgentRunReplayView getRunReplaySnapshot(Long id) {
        AgentDtos.AgentRunView run = getRunSnapshot(id);
        return run == null ? null : buildReplayView(run);
    }

    public List<AgentDtos.AgentStepView> getStepSnapshots(Long runId) {
        if (runId == null) {
            return List.of();
        }
        return agentRunMapper.findStepsByRunId(runId).stream()
                .map(this::toStepView)
                .toList();
    }

    private AgentResult executeStep(
            Long runId,
            int stepOrder,
            AgentDefinition agent,
            AgentTask task,
            Map<String, Object> memory,
            List<AgentResult> previousResults
    ) {
        LocalDateTime startedAt = LocalDateTime.now();
        long startedNanos = System.nanoTime();
        AgentResult result;
        try {
            result = agent.handler().apply(new AgentContext(runId, task, Map.copyOf(memory), List.copyOf(previousResults)));
        } catch (RuntimeException exception) {
            result = AgentResult.failure(agent.name(), agent.role(), readableError(exception));
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        long durationMs = Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);

        AgentStep step = new AgentStep();
        step.setRunId(runId);
        step.setStepOrder(stepOrder);
        step.setAgentName(result.agentName());
        step.setAgentRole(result.agentRole());
        step.setStatus(result.status());
        step.setSummary(result.summary());
        step.setInputJson(writeJson(mapOf(
                "workflowType", task.workflowType(),
                "prompt", task.prompt(),
                "payload", task.input(),
                "previousAgents", previousResults.stream().map(AgentResult::agentName).toList()
        )));
        step.setOutputJson(writeJson(result.output()));
        step.setEvidenceJson(writeJson(result.evidence()));
        step.setErrorMessage(truncate(result.errorMessage(), 1000));
        step.setConfidence(result.confidence() == null ? null : BigDecimal.valueOf(boundedConfidence(result.confidence())).setScale(4, RoundingMode.HALF_UP));
        step.setDurationMs(durationMs);
        step.setStartedAt(startedAt);
        step.setFinishedAt(finishedAt);
        agentRunMapper.insertStep(step);
        return result;
    }

    private List<AgentDefinition> buildChain(AgentTask task) {
        String workflowType = firstNonBlank(task.workflowType(), WORKFLOW_ASSISTANT_CHAT).toUpperCase(Locale.ROOT);
        List<AgentDefinition> chain = new ArrayList<>();
        chain.add(new AgentDefinition("Coordinator Agent", "任务协调", this::runCoordinator));
        switch (workflowType) {
            case WORKFLOW_SPECIES_IDENTIFY -> {
                chain.add(new AgentDefinition("Vision Review Agent", "识图复核", this::runVisionReview));
                chain.add(new AgentDefinition("Taxonomy Agent", "分类校验", this::runTaxonomy));
                chain.add(new AgentDefinition("RAG Evidence Agent", "证据召回", this::runRagEvidence));
            }
            case WORKFLOW_SPECIES_PROFILE_ASSIST -> {
                chain.add(new AgentDefinition("RAG Evidence Agent", "证据召回", this::runRagEvidence));
                chain.add(new AgentDefinition("Taxonomy Agent", "物种补全", this::runSpeciesProfileAssist));
            }
            case WORKFLOW_OBSERVATION_QA -> {
                chain.add(new AgentDefinition("Observation QA Agent", "观测质检", this::runObservationQa));
                chain.add(new AgentDefinition("Taxonomy Agent", "分类校验", this::runTaxonomy));
                chain.add(new AgentDefinition("System Data Agent", "系统数据", this::runSystemData));
            }
            case WORKFLOW_RESEARCH_REPORT -> {
                chain.add(new AgentDefinition("System Data Agent", "系统数据", this::runSystemData));
                chain.add(new AgentDefinition("RAG Evidence Agent", "证据召回", this::runRagEvidence));
                chain.add(new AgentDefinition("Report Analyst Agent", "报告分析", this::runReportAnalyst));
            }
            case WORKFLOW_KNOWLEDGE_GOVERNANCE -> {
                chain.add(new AgentDefinition("RAG Evidence Agent", "证据召回", this::runRagEvidence));
                chain.add(new AgentDefinition("Taxonomy Agent", "分类校验", this::runTaxonomy));
            }
            default -> {
                chain.add(new AgentDefinition("System Data Agent", "系统数据", this::runSystemData));
                chain.add(new AgentDefinition("RAG Evidence Agent", "证据召回", this::runRagEvidence));
            }
        }
        chain.add(new AgentDefinition("Verifier Agent", "结论验证", this::runVerifier));
        return chain;
    }

    private AgentResult runCoordinator(AgentContext context) {
        List<String> agents = inferAgentNames(context.task().workflowType());
        return AgentResult.success(
                "Coordinator Agent",
                "任务协调",
                "已识别协作类型并生成执行计划",
                mapOf(
                        "workflowType", context.task().workflowType(),
                        "subjectType", context.task().subjectType(),
                        "subjectId", context.task().subjectId(),
                        "selectedAgents", agents
                ),
                List.of(mapOf("type", "PLAN", "title", "协作计划", "description", String.join(" -> ", agents))),
                0.92d,
                STATUS_VERIFIED
        );
    }

    private AgentResult runSystemData(AgentContext context) {
        DashboardSummary summary = reportService.dashboardSummary();
        SystemDataQuery query = extractSystemDataQuery(context.task());
        List<SpeciesRow> speciesRows = query.hasSpeciesSignal()
                ? emptyIfNull(speciesMapper.findPage(
                        query.speciesKeyword(),
                        1,
                        query.protectionLevel(),
                        query.iucnStatus(),
                        query.locationKeyword(),
                        List.of(),
                        5,
                        0
                ))
                : List.of();
        String observationKeyword = firstNonBlank(query.locationKeyword(), query.ecosystemKeyword(), query.speciesKeyword());
        List<ObservationView> observationRows = StringUtils.hasText(observationKeyword)
                ? emptyIfNull(observationMapper.findPage(null, observationKeyword, null, null, 5, 0))
                : List.of();
        ObservationView subjectObservation = isSubject(context.task(), "OBSERVATION")
                ? observationMapper.findViewById(context.task().subjectId())
                : null;
        List<ObservationSpeciesView> subjectObservationSpecies = subjectObservation == null
                ? List.of()
                : emptyIfNull(observationMapper.findSpeciesViews(subjectObservation.id()));
        String ecosystemKeyword = firstNonBlank(query.ecosystemKeyword(), query.locationKeyword());
        List<Ecosystem> ecosystems = StringUtils.hasText(ecosystemKeyword)
                ? emptyIfNull(ecosystemMapper.findPage(ecosystemKeyword, null, 5, 0))
                : List.of();

        List<Map<String, Object>> speciesMatches = speciesRows.stream().map(this::toSpeciesFact).toList();
        List<Map<String, Object>> observationMatches = observationRows.stream().map(this::toObservationFact).toList();
        Map<String, Object> subjectObservationFact = subjectObservation == null
                ? null
                : toObservationFact(subjectObservation, subjectObservationSpecies);
        List<Map<String, Object>> ecosystemMatches = ecosystems.stream().map(this::toEcosystemFact).toList();

        List<Map<String, Object>> evidence = new ArrayList<>();
        evidence.add(mapOf(
                "type", "SYSTEM_DATA",
                "title", "GSMV 结构化数据",
                "description", "物种 " + summary.totalSpecies() + " 条，观测 " + summary.totalObservations() + " 条，生态系统 " + summary.totalEcosystems() + " 个"
        ));
        evidence.addAll(speciesMatches);
        evidence.addAll(observationMatches);
        if (subjectObservationFact != null) {
            evidence.add(subjectObservationFact);
        }
        evidence.addAll(ecosystemMatches);
        int matchedFacts = speciesMatches.size() + observationMatches.size() + ecosystemMatches.size() + (subjectObservationFact == null ? 0 : 1);
        Map<String, Object> output = mapOf(
                "summary", mapOf(
                        "totalSpecies", summary.totalSpecies(),
                        "totalObservations", summary.totalObservations(),
                        "totalEcosystems", summary.totalEcosystems(),
                        "totalUsers", summary.totalUsers(),
                        "recentObservationCount", summary.recentObservationCount()
                ),
                "query", query.toMap(),
                "speciesMatchCount", speciesMatches.size(),
                "observationMatchCount", observationMatches.size(),
                "ecosystemMatchCount", ecosystemMatches.size(),
                "subjectObservationMatched", subjectObservationFact != null,
                "speciesMatches", speciesMatches,
                "observationMatches", observationMatches,
                "subjectObservation", subjectObservationFact,
                "ecosystemMatches", ecosystemMatches
        );
        return AgentResult.success(
                "System Data Agent",
                "系统数据",
                matchedFacts == 0
                        ? "已读取系统核心统计，未匹配到更细的站内事实"
                        : "已读取系统统计，并匹配到 " + matchedFacts + " 条站内结构化事实",
                output,
                evidence,
                matchedFacts > 0 ? 0.92d : summary.totalSpecies() > 0 || summary.totalObservations() > 0 ? 0.86d : 0.62d,
                STATUS_VERIFIED
        );
    }

    private AgentResult runRagEvidence(AgentContext context) {
        String query = firstNonBlank(
                stringValue(context.task().input().get("ragQuery")),
                visionRagQuery(context),
                context.task().prompt(),
                stringValue(context.task().input().get("summary"))
        );
        if (!StringUtils.hasText(query)) {
            return AgentResult.success(
                    "RAG Evidence Agent",
                    "证据召回",
                    "没有足够查询文本，跳过 RAG 召回",
                    mapOf("hitCount", 0),
                    List.of(),
                    0.45d,
                    STATUS_INSUFFICIENT_EVIDENCE
            );
        }

        List<RagSearchHit> hits = ragKnowledgeService.retrieveForScenario(resolveScenario(context.task()), query, 6);
        List<Map<String, Object>> evidence = new ArrayList<>(hits.stream().map(this::toEvidenceMap).toList());
        List<Map<String, Object>> governanceEvidence = buildKnowledgeGovernanceEvidence(context.task());
        evidence.addAll(governanceEvidence);
        int governanceIssueCount = knowledgeGovernanceIssueCount(context.task());
        boolean hasGovernanceEvidence = !governanceEvidence.isEmpty();
        double confidence = hits.isEmpty()
                ? (hasGovernanceEvidence ? 0.78d : 0.52d)
                : Math.min(0.95d, 0.68d + hits.get(0).score() * 0.25d + (hasGovernanceEvidence ? 0.04d : 0.0d));
        String verificationStatus = hasGovernanceEvidence
                ? STATUS_NEEDS_REVIEW
                : hits.isEmpty() ? STATUS_INSUFFICIENT_EVIDENCE : STATUS_VERIFIED;
        return AgentResult.success(
                "RAG Evidence Agent",
                "证据召回",
                ragEvidenceSummary(hits.size(), governanceIssueCount, hasGovernanceEvidence),
                mapOf(
                        "hitCount", hits.size(),
                        "topScore", hits.isEmpty() ? null : hits.get(0).score(),
                        "governanceIssueCount", governanceIssueCount,
                        "governanceEvidence", governanceEvidence,
                        "evidenceMap", buildKnowledgeGovernanceEvidenceMap(context.task(), governanceEvidence, governanceIssueCount)
                ),
                evidence,
                confidence,
                verificationStatus
        );
    }

    private String visionRagQuery(AgentContext context) {
        Map<String, Object> visionOutput = outputOf(context.previousResults(), "Vision Review Agent");
        List<String> terms = new ArrayList<>();
        terms.add(stringValue(visionOutput.get("likelyChineseName")));
        terms.add(stringValue(visionOutput.get("likelyScientificName")));
        for (Object candidate : asCollection(visionOutput.get("candidateSummaries"))) {
            terms.add(nestedString(candidate, "chineseName"));
            terms.add(nestedString(candidate, "scientificName"));
        }
        return String.join(" ", terms.stream().filter(StringUtils::hasText).distinct().toList());
    }

    private Collection<?> combinedRagEvidence(AgentContext context) {
        List<Object> evidence = new ArrayList<>();
        evidence.addAll(asCollection(context.task().input().get("ragEvidence")));
        context.previousResults().stream()
                .filter(result -> "RAG Evidence Agent".equals(result.agentName()))
                .forEach(result -> evidence.addAll(result.evidence()));
        return evidence;
    }

    private Map<String, Object> taxonomyInput(AgentContext context) {
        Map<String, Object> input = new LinkedHashMap<>(context.task().input());
        Map<String, Object> visionOutput = outputOf(context.previousResults(), "Vision Review Agent");
        mergeIfAbsent(input, "likelyChineseName", visionOutput.get("likelyChineseName"));
        mergeIfAbsent(input, "likelyScientificName", visionOutput.get("likelyScientificName"));
        mergeIfAbsent(input, "confidence", visionOutput.get("confidence"));
        if (asCollection(input.get("candidates")).isEmpty()) {
            mergeIfAbsent(input, "candidates", visionOutput.get("candidates"));
        }
        return input;
    }

    private void mergeIfAbsent(Map<String, Object> input, String key, Object value) {
        if (!input.containsKey(key) && isMeaningful(value)) {
            input.put(key, value);
        }
    }

    private AgentResult runTaxonomy(AgentContext context) {
        Map<String, Object> input = taxonomyInput(context);
        Collection<?> related = asCollection(input.get("relatedSpeciesRecords"));
        Collection<?> candidates = asCollection(input.get("candidates"));
        Collection<?> speciesItems = asCollection(input.get("speciesItems"));
        Collection<?> warnings = asCollection(input.get("conflictWarnings"));
        Collection<?> ragEvidence = combinedRagEvidence(context);
        int candidateCount = candidates.size();
        int relatedCount = related.size();
        int speciesItemCount = speciesItems.size();
        int ragEvidenceCount = ragEvidence.size();
        List<TaxonomyName> candidateNames = extractCandidateNames(input, candidates);
        List<TaxonomyName> relatedNames = related.stream().map(this::toTaxonomyName).filter(TaxonomyName::hasAnyName).toList();
        List<SpeciesRow> systemMatches = lookupTaxonomySpecies(candidateNames, relatedNames);
        List<Map<String, Object>> issues = new ArrayList<>();
        issues.addAll(findCandidateNameIssues(candidates, candidateNames, relatedNames, systemMatches));
        issues.addAll(findProtectionConflicts(related, systemMatches));
        issues.addAll(findRagTaxonomyConflicts(ragEvidence, candidateNames, relatedNames, systemMatches));
        boolean hasWarnings = !warnings.isEmpty();
        boolean hasConflicts = !issues.isEmpty();
        List<Map<String, Object>> evidence = new ArrayList<>();
        if (relatedCount > 0) {
            evidence.add(mapOf("type", "TAXONOMY", "title", "系统内相关物种", "description", "匹配到 " + relatedCount + " 条已有物种档案"));
        }
        if (candidateCount > 0) {
            evidence.add(mapOf("type", "VISION_CANDIDATE", "title", "识图候选", "description", "候选物种 " + candidateCount + " 个"));
        }
        if (speciesItemCount > 0) {
            evidence.add(mapOf("type", "OBSERVATION_SPECIES", "title", "观测关联物种", "description", "关联物种 " + speciesItemCount + " 个"));
        }
        if (!systemMatches.isEmpty()) {
            evidence.add(mapOf(
                    "type", "TAXONOMY_SYSTEM_LOOKUP",
                    "title", "分类档案补查",
                    "description", "根据候选名补查到 " + systemMatches.size() + " 条系统物种档案",
                    "species", systemMatches.stream().map(this::toTaxonomySpeciesFact).toList()
            ));
        }
        if (ragEvidenceCount > 0) {
            evidence.add(mapOf(
                    "type", "TAXONOMY_EXTERNAL_EVIDENCE",
                    "title", "外部/知识库分类证据",
                    "description", "纳入 " + ragEvidenceCount + " 条 RAG 证据进行命名与保护信息交叉检查"
            ));
        }
        issues.forEach(issue -> evidence.add(mapOf(
                "type", "TAXONOMY_CONFLICT",
                "title", issue.get("title"),
                "description", issue.get("message"),
                "severity", issue.get("severity")
        )));
        String summary = hasWarnings || hasConflicts
                ? "分类证据存在 " + (warnings.size() + issues.size()) + " 项冲突或缺口，建议人工复核"
                : "分类命名、系统档案和证据线索已整理，可用于后续验证";
        double confidence = taxonomyConfidence(candidateCount, relatedCount, speciesItemCount, systemMatches.size(), ragEvidenceCount, issues.size(), hasWarnings);
        return AgentResult.success(
                "Taxonomy Agent",
                "分类校验",
                summary,
                mapOf(
                        "candidateCount", candidateCount,
                        "relatedSpeciesCount", relatedCount,
                        "speciesItemCount", speciesItemCount,
                        "ragEvidenceCount", ragEvidenceCount,
                        "systemLookupCount", systemMatches.size(),
                        "candidateNames", candidateNames.stream().map(TaxonomyName::toMap).toList(),
                        "relatedNames", relatedNames.stream().map(TaxonomyName::toMap).toList(),
                        "systemMatches", systemMatches.stream().map(this::toTaxonomySpeciesFact).toList(),
                        "issues", issues,
                        "warnings", warnings
                ),
                evidence,
                confidence,
                hasWarnings || hasConflicts ? STATUS_NEEDS_REVIEW : STATUS_VERIFIED
        );
    }

    private AgentResult runSpeciesProfileAssist(AgentContext context) {
        if (aiModelGateway == null) {
            return AgentResult.failure("Taxonomy Agent", "物种补全", "AI model gateway is not available");
        }
        Collection<?> ragEvidence = combinedRagEvidence(context);
        String assistType = stringValue(context.task().input().get("assistType")).toUpperCase(Locale.ROOT);
        if ("POLISH_TEXT".equals(assistType)) {
            return runSpeciesTextPolish(context, ragEvidence);
        }
        if ("TRANSLATE_SPECIES".equals(assistType)) {
            return runSpeciesTranslation(context, ragEvidence);
        }
        JsonNode node = aiModelGateway.deepSeekJson(List.of(
                AiModelGateway.message("system", SPECIES_PROFILE_ASSIST_SYSTEM_PROMPT),
                AiModelGateway.message("user", speciesProfileAssistPrompt(context, ragEvidence))
        ));
        double confidence = boundedConfidence(jsonNumber(node, "confidence"));
        Map<String, Object> output = mapOf(
                "chineseName", jsonText(node, "chineseName"),
                "scientificName", jsonText(node, "scientificName"),
                "phylumName", jsonText(node, "phylumName"),
                "className", jsonText(node, "className"),
                "orderName", jsonText(node, "orderName"),
                "familyName", jsonText(node, "familyName"),
                "genusName", jsonText(node, "genusName"),
                "protectionLevel", jsonText(node, "protectionLevel"),
                "iucnStatus", jsonText(node, "iucnStatus"),
                "description", jsonText(node, "description"),
                "morphology", jsonText(node, "morphology"),
                "habit", jsonText(node, "habit"),
                "habitat", jsonText(node, "habitat"),
                "distribution", jsonText(node, "distribution"),
                "geoRangeText", jsonText(node, "geoRangeText"),
                "summary", jsonText(node, "summary"),
                "confidence", confidence,
                "notes", jsonStringList(node.path("notes")),
                "ragEvidenceCount", ragEvidence.size()
        );
        List<Map<String, Object>> evidence = new ArrayList<>(genericEvidence(ragEvidence));
        evidence.add(mapOf(
                "type", "SPECIES_PROFILE_DRAFT",
                "title", firstNonBlank(jsonText(node, "chineseName"), jsonText(node, "scientificName"), "Species profile draft"),
                "description", firstNonBlank(jsonText(node, "summary"), jsonText(node, "description"), "Generated species profile completion draft"),
                "confidence", confidence
        ));
        return AgentResult.success(
                "Taxonomy Agent",
                "物种补全",
                confidence < 0.45d ? "物种补全证据不足" : "已生成可复核的物种补全草稿",
                output,
                evidence,
                confidence,
                confidence < 0.45d ? STATUS_INSUFFICIENT_EVIDENCE : STATUS_VERIFIED
        );
    }

    private String speciesProfileAssistPrompt(AgentContext context, Collection<?> ragEvidence) {
        return """
                Input species profile JSON:
                %s

                Retrieved evidence:
                %s

                Return JSON:
                {
                  "chineseName": "",
                  "scientificName": "",
                  "phylumName": "",
                  "className": "",
                  "orderName": "",
                  "familyName": "",
                  "genusName": "",
                  "protectionLevel": "",
                  "iucnStatus": "",
                  "description": "",
                  "morphology": "",
                  "habit": "",
                  "habitat": "",
                  "distribution": "",
                  "geoRangeText": "",
                  "summary": "",
                  "confidence": 0.0,
                  "notes": []
                }
                Keep user-provided names when credible. Leave uncertain fields empty instead of inventing facts.
                """.formatted(writeJson(context.task().input()), summarizeGenericEvidence(ragEvidence));
    }

    private AgentResult runSpeciesTextPolish(AgentContext context, Collection<?> ragEvidence) {
        JsonNode node = aiModelGateway.deepSeekJson(List.of(
                AiModelGateway.message("system", "You are a marine biodiversity archive editor. Return JSON only."),
                AiModelGateway.message("user", """
                        Field name: %s
                        Original text:
                        %s

                        Retrieved evidence:
                        %s

                        Return JSON:
                        {
                          "polishedText": "",
                          "summary": "",
                          "keywords": [],
                          "confidence": 0.0
                        }
                        Keep the meaning faithful to the original and avoid unsupported facts.
                        """.formatted(
                        stringValue(context.task().input().get("fieldName")),
                        stringValue(context.task().input().get("text")),
                        summarizeGenericEvidence(ragEvidence)
                ))
        ));
        double confidence = boundedConfidence(jsonNumber(node, "confidence"));
        Map<String, Object> output = mapOf(
                "fieldName", context.task().input().get("fieldName"),
                "polishedText", jsonText(node, "polishedText"),
                "summary", jsonText(node, "summary"),
                "keywords", jsonStringList(node.path("keywords")),
                "confidence", confidence,
                "ragEvidenceCount", ragEvidence.size()
        );
        List<Map<String, Object>> evidence = new ArrayList<>(genericEvidence(ragEvidence));
        evidence.add(mapOf(
                "type", "SPECIES_TEXT_POLISH",
                "title", "Species text polish draft",
                "description", firstNonBlank(jsonText(node, "summary"), jsonText(node, "polishedText")),
                "confidence", confidence
        ));
        return AgentResult.success(
                "Taxonomy Agent",
                "物种文本润色",
                "已生成可复核的物种文本润色草稿",
                output,
                evidence,
                confidence,
                confidence < 0.45d ? STATUS_INSUFFICIENT_EVIDENCE : STATUS_VERIFIED
        );
    }

    private AgentResult runSpeciesTranslation(AgentContext context, Collection<?> ragEvidence) {
        JsonNode node = aiModelGateway.deepSeekJson(List.of(
                AiModelGateway.message("system", "You are a multilingual marine biodiversity translation assistant. Return JSON only."),
                AiModelGateway.message("user", """
                        Target language: %s
                        Species profile JSON:
                        %s

                        Retrieved evidence:
                        %s

                        Return JSON:
                        {
                          "description": "",
                          "morphology": "",
                          "habit": "",
                          "habitat": "",
                          "distribution": "",
                          "geoRangeText": "",
                          "summary": "",
                          "confidence": 0.0
                        }
                        Preserve scientific names and leave empty fields empty.
                        """.formatted(
                        stringValue(context.task().input().get("targetLanguage")),
                        writeJson(context.task().input()),
                        summarizeGenericEvidence(ragEvidence)
                ))
        ));
        double confidence = boundedConfidence(jsonNumber(node, "confidence"));
        Map<String, Object> output = mapOf(
                "targetLanguage", context.task().input().get("targetLanguage"),
                "description", jsonText(node, "description"),
                "morphology", jsonText(node, "morphology"),
                "habit", jsonText(node, "habit"),
                "habitat", jsonText(node, "habitat"),
                "distribution", jsonText(node, "distribution"),
                "geoRangeText", jsonText(node, "geoRangeText"),
                "summary", jsonText(node, "summary"),
                "confidence", confidence,
                "ragEvidenceCount", ragEvidence.size()
        );
        List<Map<String, Object>> evidence = new ArrayList<>(genericEvidence(ragEvidence));
        evidence.add(mapOf(
                "type", "SPECIES_TRANSLATION_DRAFT",
                "title", "Species translation draft",
                "description", jsonText(node, "summary"),
                "confidence", confidence
        ));
        return AgentResult.success(
                "Taxonomy Agent",
                "物种翻译",
                "已生成可复核的物种翻译草稿",
                output,
                evidence,
                confidence,
                confidence < 0.45d ? STATUS_INSUFFICIENT_EVIDENCE : STATUS_VERIFIED
        );
    }

    private String summarizeGenericEvidence(Collection<?> evidence) {
        List<String> lines = evidence.stream()
                .limit(8)
                .map(item -> firstNonBlank(
                        nestedString(item, "title"),
                        nestedString(item, "summary"),
                        nestedString(item, "description"),
                        nestedString(item, "sourcePath")
                ) + ": " + firstNonBlank(
                        nestedString(item, "summary"),
                        nestedString(item, "description"),
                        nestedString(item, "contentSnippet"),
                        nestedString(item, "content")
                ))
                .filter(StringUtils::hasText)
                .toList();
        return lines.isEmpty() ? "None" : String.join("\n", lines);
    }

    private List<Map<String, Object>> genericEvidence(Collection<?> evidence) {
        return evidence.stream()
                .map(this::genericEvidenceItem)
                .filter(item -> !item.isEmpty())
                .limit(12)
                .toList();
    }

    private Map<String, Object> genericEvidenceItem(Object item) {
        String title = firstNonBlank(nestedString(item, "title"), nestedString(item, "summary"), nestedString(item, "sourcePath"));
        if (!StringUtils.hasText(title)) {
            return Map.of();
        }
        return mapOf(
                "type", firstNonBlank(nestedString(item, "type"), "RAG"),
                "title", title,
                "description", firstNonBlank(nestedString(item, "summary"), nestedString(item, "description"), nestedString(item, "contentSnippet")),
                "sourcePath", nestedString(item, "sourcePath"),
                "score", fieldValue(item, "score")
        );
    }

    private AgentResult runObservationQa(AgentContext context) {
        Collection<?> anomalies = asCollection(context.task().input().get("anomalies"));
        Collection<?> issues = asCollection(context.task().input().get("issues"));
        Collection<?> reviewNotes = asCollection(context.task().input().get("reviewNotes"));
        ObservationQaSnapshot snapshot = buildObservationQaSnapshot(context);
        List<Map<String, Object>> qaIssues = inspectObservationQa(snapshot);
        boolean hasHighRuleIssue = qaIssues.stream()
                .anyMatch(issue -> "HIGH".equalsIgnoreCase(stringValue(issue.get("severity"))));
        boolean needsReview = booleanValue(context.task().input().get("needsReview"))
                || !anomalies.isEmpty()
                || !issues.isEmpty()
                || !reviewNotes.isEmpty()
                || hasHighRuleIssue;
        Integer score = integerValue(context.task().input().get("score"));
        String grade = firstNonBlank(stringValue(context.task().input().get("grade")), score == null ? "" : score >= 85 ? "HIGH" : score >= 70 ? "MEDIUM" : "LOW");
        List<Map<String, Object>> actionItems = buildObservationQaActionItems(snapshot, qaIssues, issues, reviewNotes);
        Map<String, Object> reviewTaskDraft = needsReview
                ? buildObservationQaReviewTaskDraft(snapshot, qaIssues, actionItems, score, grade)
                : Map.of();
        double confidence = observationQaConfidence(score, needsReview, qaIssues);
        return AgentResult.success(
                "Observation QA Agent",
                "Observation quality",
                needsReview ? "Observation record requires QA review" : "Observation record passed QA rules",
                mapOf(
                        "anomalyCount", anomalies.size(),
                        "issueCount", issues.size(),
                        "reviewNoteCount", reviewNotes.size(),
                        "qaIssueCount", qaIssues.size(),
                        "score", score,
                        "grade", grade,
                        "needsReview", needsReview,
                        "source", snapshot.source(),
                        "observationId", snapshot.id(),
                        "ecosystemName", snapshot.ecosystemName(),
                        "observedAt", snapshot.observedAt() == null ? null : snapshot.observedAt().toString(),
                        "locationLat", snapshot.locationLat(),
                        "locationLng", snapshot.locationLng(),
                        "locationName", snapshot.locationName(),
                        "speciesCount", snapshot.speciesItems().size(),
                        "environmentPresent", !isEnvironmentBlank(snapshot.environment()),
                        "qaIssues", qaIssues,
                        "actionItems", actionItems,
                        "reviewTaskDraft", reviewTaskDraft
                ),
                observationQaEvidence(snapshot, qaIssues, needsReview),
                confidence,
                needsReview ? STATUS_NEEDS_REVIEW : STATUS_VERIFIED
        );
    }

    private AgentResult runObservationQaLegacy(AgentContext context) {
        Collection<?> anomalies = asCollection(context.task().input().get("anomalies"));
        Collection<?> issues = asCollection(context.task().input().get("issues"));
        Collection<?> reviewNotes = asCollection(context.task().input().get("reviewNotes"));
        boolean needsReview = booleanValue(context.task().input().get("needsReview")) || !anomalies.isEmpty() || !issues.isEmpty() || !reviewNotes.isEmpty();
        Integer score = integerValue(context.task().input().get("score"));
        String grade = firstNonBlank(stringValue(context.task().input().get("grade")), score == null ? "" : score >= 85 ? "HIGH" : score >= 70 ? "MEDIUM" : "LOW");
        return AgentResult.success(
                "Observation QA Agent",
                "观测质检",
                needsReview ? "观测记录存在需要复核的质量提示" : "观测记录未发现明显质量风险",
                mapOf(
                        "anomalyCount", anomalies.size(),
                        "issueCount", issues.size(),
                        "reviewNoteCount", reviewNotes.size(),
                        "score", score,
                        "grade", grade,
                        "needsReview", needsReview
                ),
                List.of(mapOf(
                        "type", "OBSERVATION_QA",
                        "title", "观测质量检查",
                        "description", needsReview ? "发现质量提示或人工复核建议" : "规则检查未发现明显异常"
                )),
                score == null ? (needsReview ? 0.64d : 0.82d) : Math.max(0.35d, Math.min(0.96d, score / 100.0d)),
                needsReview ? STATUS_NEEDS_REVIEW : STATUS_VERIFIED
        );
    }

    private ObservationQaSnapshot buildObservationQaSnapshot(AgentContext context) {
        Map<String, Object> input = context.task().input();
        if (isSubject(context.task(), "OBSERVATION")) {
            ObservationView view = observationMapper.findViewById(context.task().subjectId());
            if (view != null) {
                return new ObservationQaSnapshot(
                        "STORED_OBSERVATION",
                        view.id(),
                        view.ecosystemName(),
                        view.observedAt(),
                        view.locationLat(),
                        view.locationLng(),
                        view.locationName(),
                        readEnvironmentObject(view.envJson()),
                        emptyIfNull(observationMapper.findSpeciesViews(view.id())),
                        view.note()
                );
            }
        }
        return new ObservationQaSnapshot(
                firstNonBlank(context.task().subjectType(), "OBSERVATION_DRAFT"),
                context.task().subjectId(),
                stringValue(input.get("ecosystemName")),
                localDateTimeValue(input.get("observedAt")),
                decimalValue(input.get("locationLat")),
                decimalValue(input.get("locationLng")),
                stringValue(input.get("locationName")),
                input.get("environment"),
                asCollection(input.get("speciesItems")),
                stringValue(input.get("note"))
        );
    }

    private List<Map<String, Object>> inspectObservationQa(ObservationQaSnapshot snapshot) {
        List<Map<String, Object>> qaIssues = new ArrayList<>();
        if (!StringUtils.hasText(snapshot.ecosystemName())) {
            addObservationQaIssue(qaIssues, "ECOSYSTEM_MISSING", "MEDIUM", "Missing ecosystem", "Observation has no ecosystem context.");
        }
        if (snapshot.observedAt() == null) {
            addObservationQaIssue(qaIssues, "OBSERVED_AT_MISSING", "HIGH", "Missing observation time", "Observation time is required for seasonal and audit analysis.");
        } else if (snapshot.observedAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
            addObservationQaIssue(qaIssues, "OBSERVED_AT_FUTURE", "HIGH", "Future observation time", "Observation time is later than the current system time.");
        }
        inspectCoordinate(qaIssues, "LATITUDE_MISSING", "LATITUDE_OUT_OF_RANGE", "Latitude", snapshot.locationLat(), -90.0d, 90.0d);
        inspectCoordinate(qaIssues, "LONGITUDE_MISSING", "LONGITUDE_OUT_OF_RANGE", "Longitude", snapshot.locationLng(), -180.0d, 180.0d);
        if (snapshot.speciesItems().isEmpty()) {
            addObservationQaIssue(qaIssues, "SPECIES_EMPTY", "HIGH", "Missing species", "Observation is not linked to any species record.");
        } else {
            inspectSpeciesCounts(qaIssues, snapshot.speciesItems());
        }
        if (isEnvironmentBlank(snapshot.environment())) {
            addObservationQaIssue(qaIssues, "ENVIRONMENT_MISSING", "MEDIUM", "Missing environment", "Water temperature, salinity, pH, oxygen and depth are all empty.");
        } else {
            inspectEnvironment(qaIssues, snapshot.environment());
        }
        return qaIssues;
    }

    private void inspectCoordinate(
            List<Map<String, Object>> qaIssues,
            String missingCode,
            String rangeCode,
            String label,
            BigDecimal value,
            double min,
            double max
    ) {
        if (value == null) {
            addObservationQaIssue(qaIssues, missingCode, "HIGH", "Missing " + label, label + " is required for spatial validation.");
            return;
        }
        double numericValue = value.doubleValue();
        if (numericValue < min || numericValue > max) {
            addObservationQaIssue(qaIssues, rangeCode, "HIGH", label + " out of range", label + " must be between " + min + " and " + max + ".");
        }
    }

    private void inspectSpeciesCounts(List<Map<String, Object>> qaIssues, Collection<?> speciesItems) {
        int index = 0;
        for (Object item : speciesItems) {
            index++;
            Integer count = integerValue(fieldValue(item, "countEstimated"));
            if (count == null) {
                continue;
            }
            String speciesName = firstNonBlank(nestedString(item, "chineseName"), nestedString(item, "scientificName"), "species item " + index);
            if (count < 0) {
                addObservationQaIssue(qaIssues, "SPECIES_COUNT_NEGATIVE", "HIGH", "Negative species count", speciesName + " has a negative estimated count.");
            } else if (count == 0) {
                addObservationQaIssue(qaIssues, "SPECIES_COUNT_ZERO", "MEDIUM", "Zero species count", speciesName + " has a zero estimated count.");
            } else if (count > 10000) {
                addObservationQaIssue(qaIssues, "SPECIES_COUNT_EXTREME", "MEDIUM", "Extreme species count", speciesName + " has an unusually high estimated count.");
            }
        }
    }

    private void inspectEnvironment(List<Map<String, Object>> qaIssues, Object environment) {
        inspectRange(qaIssues, "WATER_TEMPERATURE_OUT_OF_RANGE", "HIGH", "Water temperature out of range", environment, "waterTemperature", -2.0d, 40.0d);
        inspectRange(qaIssues, "SALINITY_OUT_OF_RANGE", "HIGH", "Salinity out of range", environment, "salinity", 0.0d, 45.0d);
        inspectRange(qaIssues, "PH_OUT_OF_RANGE", "HIGH", "pH out of range", environment, "ph", 6.5d, 9.0d);
        Double dissolvedOxygen = fieldDouble(environment, "dissolvedOxygen");
        if (dissolvedOxygen != null && (dissolvedOxygen < 3.0d || dissolvedOxygen > 20.0d)) {
            addObservationQaIssue(qaIssues, "DISSOLVED_OXYGEN_OUT_OF_RANGE", "HIGH", "Dissolved oxygen out of range", "Dissolved oxygen should be between 3 and 20 mg/L for routine QA.");
        }
        Double depthMeters = fieldDouble(environment, "depthMeters");
        if (depthMeters != null && depthMeters < 0.0d) {
            addObservationQaIssue(qaIssues, "DEPTH_NEGATIVE", "HIGH", "Negative depth", "Depth cannot be negative.");
        }
    }

    private void inspectRange(
            List<Map<String, Object>> qaIssues,
            String code,
            String severity,
            String title,
            Object source,
            String field,
            double min,
            double max
    ) {
        Double value = fieldDouble(source, field);
        if (value != null && (value < min || value > max)) {
            addObservationQaIssue(qaIssues, code, severity, title, field + " must be between " + min + " and " + max + ".");
        }
    }

    private List<Map<String, Object>> observationQaEvidence(
            ObservationQaSnapshot snapshot,
            List<Map<String, Object>> qaIssues,
            boolean needsReview
    ) {
        List<Map<String, Object>> evidence = new ArrayList<>();
        evidence.add(mapOf(
                "type", "OBSERVATION_QA",
                "title", "Observation QA",
                "description", needsReview ? "Rule checks found review signals." : "Rule checks found no high-risk issue."
        ));
        if (snapshot.hasObservationFacts()) {
            evidence.add(mapOf(
                    "type", "OBSERVATION_QA_SOURCE",
                    "title", "Observation QA source",
                    "description", snapshot.sourceDescription(),
                    "sourceType", snapshot.source(),
                    "sourceId", snapshot.id()
            ));
        }
        qaIssues.forEach(issue -> evidence.add(mapOf(
                "type", "OBSERVATION_QA_RULE",
                "title", issue.get("title"),
                "description", issue.get("message"),
                "severity", issue.get("severity"),
                "code", issue.get("code")
        )));
        return evidence;
    }

    private List<Map<String, Object>> buildObservationQaActionItems(
            ObservationQaSnapshot snapshot,
            List<Map<String, Object>> qaIssues,
            Collection<?> sourceIssues,
            Collection<?> reviewNotes
    ) {
        List<Map<String, Object>> actions = new ArrayList<>();
        for (Map<String, Object> issue : qaIssues) {
            String code = stringValue(issue.get("code"));
            String severity = firstNonBlank(stringValue(issue.get("severity")), "LOW").toUpperCase(Locale.ROOT);
            String priority = "HIGH".equals(severity) ? "HIGH" : "MEDIUM".equals(severity) ? "MEDIUM" : "LOW";
            actions.add(mapOf(
                    "code", "QA_ACTION_" + code,
                    "priority", priority,
                    "action", observationQaActionText(code, issue),
                    "source", code
            ));
        }
        int index = 1;
        for (String issue : stringItems(sourceIssues)) {
            actions.add(mapOf(
                    "code", "QA_SOURCE_ISSUE_" + index,
                    "priority", "MEDIUM",
                    "action", "复核 AI 质量评分提示：" + issue,
                    "source", "SOURCE_ISSUE"
            ));
            index++;
        }
        int noteIndex = 1;
        for (String note : stringItems(reviewNotes)) {
            actions.add(mapOf(
                    "code", "QA_REVIEW_NOTE_" + noteIndex,
                    "priority", "MEDIUM",
                    "action", "补充人工核验说明：" + note,
                    "source", "REVIEW_NOTE"
            ));
            noteIndex++;
        }
        if (actions.isEmpty()) {
            actions.add(mapOf(
                    "code", "QA_MAINTAIN_SAMPLING_REVIEW",
                    "priority", "LOW",
                    "action", "保持常规抽检节奏，并在正式报告前抽样复核关键观测记录。",
                    "source", snapshot.source()
            ));
        }
        return distinctActions(actions).stream().limit(10).toList();
    }

    private List<Map<String, Object>> distinctActions(List<Map<String, Object>> actions) {
        List<Map<String, Object>> values = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Map<String, Object> action : actions) {
            String key = firstNonBlank(stringValue(action.get("code")), stringValue(action.get("action")));
            if (!seen.contains(key)) {
                seen.add(key);
                values.add(action);
            }
        }
        return values;
    }

    private String observationQaActionText(String code, Map<String, Object> issue) {
        return switch (code) {
            case "ECOSYSTEM_MISSING" -> "补充生态系统归属，确保观测能进入生态系统统计。";
            case "OBSERVED_AT_MISSING", "OBSERVED_AT_FUTURE" -> "核对观测时间，修正缺失或未来时间。";
            case "LATITUDE_MISSING", "LONGITUDE_MISSING", "LATITUDE_OUT_OF_RANGE", "LONGITUDE_OUT_OF_RANGE" -> "复核经纬度坐标，必要时回看地图点选位置。";
            case "SPECIES_EMPTY" -> "至少关联一个现场确认或待确认物种，再进入统计分析。";
            case "SPECIES_COUNT_NEGATIVE", "SPECIES_COUNT_ZERO", "SPECIES_COUNT_EXTREME" -> "复核物种估算数量，补充数量口径或现场说明。";
            case "ENVIRONMENT_MISSING" -> "补充水温、盐度、pH、溶解氧和水深等关键环境参数。";
            case "WATER_TEMPERATURE_OUT_OF_RANGE", "SALINITY_OUT_OF_RANGE", "PH_OUT_OF_RANGE", "DISSOLVED_OXYGEN_OUT_OF_RANGE", "DEPTH_NEGATIVE" -> "复核环境参数单位、仪器读数和录入值。";
            default -> "处理质量问题：" + firstNonBlank(stringValue(issue.get("message")), stringValue(issue.get("title")), code);
        };
    }

    private Map<String, Object> buildObservationQaReviewTaskDraft(
            ObservationQaSnapshot snapshot,
            List<Map<String, Object>> qaIssues,
            List<Map<String, Object>> actionItems,
            Integer score,
            String grade
    ) {
        String priority = qaIssues.stream().anyMatch(issue -> "HIGH".equalsIgnoreCase(stringValue(issue.get("severity"))))
                ? "HIGH"
                : qaIssues.stream().anyMatch(issue -> "MEDIUM".equalsIgnoreCase(stringValue(issue.get("severity")))) ? "MEDIUM" : "LOW";
        return mapOf(
                "taskType", "OBSERVATION_QA_REVIEW",
                "subjectType", snapshot.source(),
                "subjectId", snapshot.id(),
                "priority", priority,
                "score", score,
                "grade", grade,
                "title", "观测质量复核：" + firstNonBlank(snapshot.locationName(), snapshot.ecosystemName(), snapshot.id() == null ? "草稿记录" : "观测 #" + snapshot.id()),
                "summary", "发现 " + qaIssues.size() + " 项质量问题，建议执行 " + actionItems.size() + " 个修复动作。",
                "qaIssues", qaIssues,
                "actionItems", actionItems
        );
    }

    private double observationQaConfidence(Integer score, boolean needsReview, List<Map<String, Object>> qaIssues) {
        double confidence = score == null ? (needsReview ? 0.64d : 0.82d) : score / 100.0d;
        for (Map<String, Object> issue : qaIssues) {
            String severity = stringValue(issue.get("severity"));
            if ("HIGH".equalsIgnoreCase(severity)) {
                confidence -= 0.08d;
            } else if ("MEDIUM".equalsIgnoreCase(severity)) {
                confidence -= 0.04d;
            } else {
                confidence -= 0.02d;
            }
        }
        return Math.max(0.35d, Math.min(0.96d, boundedConfidence(confidence)));
    }

    private void addObservationQaIssue(List<Map<String, Object>> qaIssues, String code, String severity, String title, String message) {
        qaIssues.add(mapOf(
                "code", code,
                "severity", severity,
                "title", title,
                "message", message
        ));
    }

    private Object readEnvironmentObject(String envJson) {
        if (!StringUtils.hasText(envJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(envJson, Object.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isEnvironmentBlank(Object environment) {
        if (environment == null) {
            return true;
        }
        boolean hasNumericValue = List.of("waterTemperature", "salinity", "ph", "dissolvedOxygen", "transparency", "depthMeters")
                .stream()
                .map(field -> fieldValue(environment, field))
                .anyMatch(Objects::nonNull);
        return !hasNumericValue
                && !StringUtils.hasText(nestedString(environment, "weather"))
                && !StringUtils.hasText(nestedString(environment, "seaState"));
    }

    private AgentResult runVisionReview(AgentContext context) {
        Map<String, Object> input = visionReviewInput(context);
        double confidence = boundedConfidence(numberValue(input.get("confidence"), 0.0d));
        boolean needsReview = booleanValue(input.get("needsHumanReview"));
        Collection<?> warnings = asCollection(input.get("conflictWarnings"));
        Collection<?> candidates = asCollection(input.get("candidates"));
        Collection<?> ragEvidence = asCollection(input.get("ragEvidence"));
        boolean ragEvidencePending = booleanValue(input.get("ragEvidencePending"));
        List<Map<String, Object>> candidateSummaries = summarizeVisionCandidates(candidates);
        List<String> reviewReasons = buildVisionReviewReasons(confidence, needsReview, warnings, candidateSummaries, ragEvidence, ragEvidencePending);
        boolean shouldCreateHumanReviewTicket = needsReview || !warnings.isEmpty() || !reviewReasons.isEmpty();
        Map<String, Object> reviewTicketDraft = shouldCreateHumanReviewTicket
                ? buildVisionReviewTicketDraft(input, confidence, candidateSummaries, warnings, ragEvidence, reviewReasons)
                : Map.of();
        List<Map<String, Object>> evidence = visionReviewEvidence(candidateSummaries, warnings, ragEvidence, reviewReasons, shouldCreateHumanReviewTicket);
        return AgentResult.success(
                "Vision Review Agent",
                "识图复核",
                shouldCreateHumanReviewTicket ? "视觉识别结果建议进入人工复核" : "视觉识别结果置信度较高",
                mapOf(
                        "confidence", confidence,
                        "needsHumanReview", needsReview,
                        "likelyChineseName", input.get("likelyChineseName"),
                        "likelyScientificName", input.get("likelyScientificName"),
                        "reasoning", input.get("reasoning"),
                        "candidates", candidates,
                        "candidateCount", candidates.size(),
                        "warningCount", warnings.size(),
                        "ragEvidenceCount", ragEvidence.size(),
                        "candidateSummaries", candidateSummaries,
                        "reviewReasons", reviewReasons,
                        "shouldCreateHumanReviewTicket", shouldCreateHumanReviewTicket,
                        "reviewTicketDraft", reviewTicketDraft
                ),
                evidence,
                confidence,
                shouldCreateHumanReviewTicket ? STATUS_NEEDS_REVIEW : STATUS_VERIFIED
        );
    }

    private Map<String, Object> visionReviewInput(AgentContext context) {
        Map<String, Object> input = new LinkedHashMap<>(context.task().input());
        if (asCollection(input.get("candidates")).isEmpty()) {
            mergeVisionRecognition(input, runVisionRecognition(context));
        }
        double confidence = boundedConfidence(numberValue(input.get("confidence"), 0.0d));
        if (!input.containsKey("confidence")) {
            input.put("confidence", confidence);
        }
        if (!input.containsKey("needsHumanReview")) {
            double threshold = numberValue(input.get("lowConfidenceThreshold"), 0.68d);
            input.put("needsHumanReview", confidence < threshold);
        }
        return input;
    }

    private Map<String, Object> runVisionRecognition(AgentContext context) {
        if (aiModelGateway == null) {
            return Map.of();
        }
        Object imageBytesValue = context.task().transientInput().get("imageBytes");
        if (!(imageBytesValue instanceof byte[] imageBytes) || imageBytes.length == 0) {
            return Map.of();
        }
        String contentType = firstNonBlank(stringValue(context.task().transientInput().get("contentType")), "image/jpeg");
        JsonNode result = aiModelGateway.bailianVisionJson(
                VISION_REVIEW_SYSTEM_PROMPT,
                VISION_REVIEW_USER_PROMPT,
                imageBytes,
                contentType
        );
        List<Map<String, Object>> candidates = parseVisionCandidates(result.path("candidates"));
        Map<String, Object> topCandidate = candidates.isEmpty() ? Map.of() : candidates.get(0);
        return mapOf(
                "likelyChineseName", firstNonBlank(jsonText(result, "likelyChineseName"), nestedString(topCandidate, "chineseName")),
                "likelyScientificName", firstNonBlank(jsonText(result, "likelyScientificName"), nestedString(topCandidate, "scientificName")),
                "confidence", boundedConfidence(jsonNumber(result, "confidence")),
                "reasoning", jsonText(result, "reasoning"),
                "candidates", candidates,
                "visionSource", "Vision Review Agent"
        );
    }

    private void mergeVisionRecognition(Map<String, Object> input, Map<String, Object> recognition) {
        recognition.forEach((key, value) -> {
            if (!input.containsKey(key) && isMeaningful(value)) {
                input.put(key, value);
            }
        });
    }

    private List<Map<String, Object>> parseVisionCandidates(JsonNode candidatesNode) {
        if (!candidatesNode.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (JsonNode item : candidatesNode) {
            candidates.add(mapOf(
                    "chineseName", jsonText(item, "chineseName"),
                    "scientificName", jsonText(item, "scientificName"),
                    "confidence", boundedConfidence(jsonNumber(item, "confidence")),
                    "reason", jsonText(item, "reason")
            ));
        }
        return candidates.stream()
                .sorted(Comparator.comparingDouble(candidate -> -numberValue(candidate.get("confidence"), 0.0d)))
                .limit(5)
                .toList();
    }

    private String jsonText(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() || fieldNode.isNull() ? "" : fieldNode.asText("").trim();
    }

    private double jsonNumber(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isNumber() ? fieldNode.asDouble() : 0.0d;
    }

    private List<String> jsonStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private List<Map<String, Object>> summarizeVisionCandidates(Collection<?> candidates) {
        List<Map<String, Object>> values = new ArrayList<>();
        int rank = 1;
        for (Object candidate : candidates) {
            double confidence = boundedConfidence(fieldNumber(candidate, "confidence", 0.0d));
            values.add(mapOf(
                    "rank", rank,
                    "chineseName", nestedString(candidate, "chineseName"),
                    "scientificName", nestedString(candidate, "scientificName"),
                    "confidence", confidence,
                    "reason", nestedString(candidate, "reason"),
                    "confidenceBand", confidence >= 0.82d ? "HIGH" : confidence >= 0.68d ? "MEDIUM" : "LOW"
            ));
            rank++;
        }
        return values.stream()
                .sorted(Comparator.comparingDouble(item -> -numberValue(item.get("confidence"), 0.0d)))
                .limit(5)
                .toList();
    }

    private List<String> buildVisionReviewReasons(
            double confidence,
            boolean needsReview,
            Collection<?> warnings,
            List<Map<String, Object>> candidateSummaries,
            Collection<?> ragEvidence,
            boolean ragEvidencePending
    ) {
        List<String> reasons = new ArrayList<>();
        if (needsReview || confidence < 0.68d) {
            reasons.add("视觉模型置信度低于复核阈值");
        }
        if (candidateSummaries.isEmpty()) {
            reasons.add("未返回可审计候选物种");
        }
        if (!warnings.isEmpty()) {
            reasons.add("识图结果与 RAG 或系统证据存在冲突");
        }
        if (!ragEvidencePending && ragEvidence.isEmpty() && confidence < 0.82d) {
            reasons.add("中低置信度结果缺少知识库证据支撑");
        }
        return reasons.stream().distinct().toList();
    }

    private Map<String, Object> buildVisionReviewTicketDraft(
            Map<String, Object> input,
            double confidence,
            List<Map<String, Object>> candidateSummaries,
            Collection<?> warnings,
            Collection<?> ragEvidence,
            List<String> reviewReasons
    ) {
        Map<String, Object> topCandidate = candidateSummaries.isEmpty() ? Map.of() : candidateSummaries.get(0);
        return mapOf(
                "sourceType", "SPECIES_IDENTIFY",
                "likelyChineseName", firstNonBlank(stringValue(input.get("likelyChineseName")), nestedString(topCandidate, "chineseName")),
                "likelyScientificName", firstNonBlank(stringValue(input.get("likelyScientificName")), nestedString(topCandidate, "scientificName")),
                "confidence", confidence,
                "needsHumanReview", true,
                "reasoning", input.get("reasoning"),
                "reviewReasons", reviewReasons,
                "candidateSummaries", candidateSummaries,
                "relatedSpeciesRecords", input.get("relatedSpeciesRecords"),
                "ragEvidenceCount", ragEvidence.size(),
                "conflictWarnings", stringItems(warnings),
                "submitNote", "Agent 建议人工复核：" + String.join("；", reviewReasons)
        );
    }

    private List<Map<String, Object>> visionReviewEvidence(
            List<Map<String, Object>> candidateSummaries,
            Collection<?> warnings,
            Collection<?> ragEvidence,
            List<String> reviewReasons,
            boolean shouldCreateHumanReviewTicket
    ) {
        List<Map<String, Object>> evidence = new ArrayList<>();
        evidence.add(mapOf(
                "type", "VISION",
                "title", "视觉模型识别",
                "description", "候选 " + candidateSummaries.size() + " 个，建议复核：" + (shouldCreateHumanReviewTicket ? "是" : "否")
        ));
        candidateSummaries.stream().limit(3).forEach(candidate -> evidence.add(mapOf(
                "type", "VISION_CANDIDATE",
                "title", firstNonBlank(nestedString(candidate, "chineseName"), nestedString(candidate, "scientificName"), "候选物种"),
                "description", "候选排名 " + nestedString(candidate, "rank") + "，置信度 " + Math.round(numberValue(candidate.get("confidence"), 0.0d) * 100) + "%",
                "confidence", candidate.get("confidence")
        )));
        for (String warning : stringItems(warnings)) {
            evidence.add(mapOf(
                    "type", "VISION_RAG_CONFLICT",
                    "title", "识图证据冲突",
                    "description", warning,
                    "severity", "HIGH"
            ));
        }
        if (!reviewReasons.isEmpty()) {
            evidence.add(mapOf(
                    "type", "HUMAN_REVIEW_RECOMMENDATION",
                    "title", "人工复核建议",
                    "description", String.join("；", reviewReasons),
                    "severity", "HIGH"
            ));
        }
        if (!ragEvidence.isEmpty()) {
            evidence.add(mapOf(
                    "type", "VISION_RAG_EVIDENCE",
                    "title", "识图 RAG 证据",
                    "description", "已纳入 " + ragEvidence.size() + " 条知识库证据进行候选校验"
            ));
        }
        return evidence;
    }

    private AgentResult runReportAnalyst(AgentContext context) {
        Collection<?> highlights = asCollection(context.task().input().get("highlights"));
        Collection<?> risks = asCollection(context.task().input().get("risks"));
        Collection<?> recommendations = asCollection(context.task().input().get("recommendations"));
        Collection<?> evidence = asCollection(context.task().input().get("evidence"));
        int days = Math.min(Math.max(integerValue(context.task().input().get("days")) == null ? 30 : integerValue(context.task().input().get("days")), 1), 365);
        DashboardSummary summary = reportService.dashboardSummary();
        List<NameValuePoint> trend = emptyIfNull(reportService.observationTrend(days));
        List<NameValuePoint> observers = emptyIfNull(reportService.observationActivityByUser(days));
        List<EcosystemAnalyticsPoint> ecosystems = emptyIfNull(reportService.ecosystemAnalytics());
        List<NameValuePoint> protection = emptyIfNull(reportService.protectionLevelDistribution());
        List<Map<String, Object>> trendSignals = buildReportTrendSignals(summary, trend, ecosystems);
        List<Map<String, Object>> riskSignals = buildReportRiskSignals(summary, risks, evidence, context.previousResults(), observers, protection);
        List<Map<String, Object>> actionItems = buildReportActionItems(recommendations, riskSignals, trendSignals);
        List<Map<String, Object>> evidenceMap = buildReportEvidenceMap(highlights, risks, recommendations, evidence, context.previousResults(), summary);
        List<Map<String, Object>> agentEvidence = reportAnalystEvidence(trendSignals, riskSignals, actionItems, evidenceMap);
        Map<String, Object> draft = buildReportAgentDraft(
                context.task().input().get("title"),
                context.task().input().get("summary"),
                highlights,
                risks,
                recommendations,
                evidence,
                trendSignals,
                riskSignals,
                actionItems,
                evidenceMap
        );
        boolean evidenceGap = riskSignals.stream()
                .anyMatch(risk -> "EVIDENCE_GAP".equals(risk.get("code")) || "DATA_EMPTY".equals(risk.get("code")));
        double confidence = reportAnalystConfidence(highlights, risks, recommendations, evidence, trendSignals, riskSignals);
        return AgentResult.success(
                "Report Analyst Agent",
                "Report analysis",
                "Structured report analysis prepared with trends, risks, actions and evidence mapping",
                mapOf(
                        "title", context.task().input().get("title"),
                        "reportType", context.task().input().get("reportType"),
                        "days", days,
                        "highlightCount", highlights.size(),
                        "riskCount", risks.size(),
                        "recommendationCount", recommendations.size(),
                        "evidenceCount", evidence.size(),
                        "trendSignals", trendSignals,
                        "riskSignals", riskSignals,
                        "actionItems", actionItems,
                        "evidenceMap", evidenceMap,
                        "stats", mapOf(
                                "totalSpecies", summary.totalSpecies(),
                                "totalObservations", summary.totalObservations(),
                                "totalEcosystems", summary.totalEcosystems(),
                                "recentObservationCount", summary.recentObservationCount(),
                                "trendPointCount", trend.size(),
                                "observerCount", observers.size(),
                                "ecosystemPointCount", ecosystems.size(),
                                "protectionBucketCount", protection.size()
                        ),
                        "draft", draft
                ),
                agentEvidence,
                confidence,
                evidenceGap ? STATUS_INSUFFICIENT_EVIDENCE : STATUS_VERIFIED
        );
    }

    private Map<String, Object> buildReportAgentDraft(
            Object title,
            Object summary,
            Collection<?> highlights,
            Collection<?> risks,
            Collection<?> recommendations,
            Collection<?> evidence,
            List<Map<String, Object>> trendSignals,
            List<Map<String, Object>> riskSignals,
            List<Map<String, Object>> actionItems,
            List<Map<String, Object>> evidenceMap
    ) {
        List<String> draftHighlights = new ArrayList<>(stringItems(highlights));
        trendSignals.stream()
                .filter(signal -> !"LOW".equalsIgnoreCase(nestedString(signal, "severity")))
                .map(signal -> "Agent 趋势发现：" + nestedString(signal, "message"))
                .forEach(value -> addDistinctLimited(draftHighlights, value, 8));

        List<String> draftRisks = new ArrayList<>(stringItems(risks));
        riskSignals.stream()
                .filter(signal -> !"LOW".equalsIgnoreCase(nestedString(signal, "severity")))
                .map(signal -> "Agent 风险提示：" + nestedString(signal, "message"))
                .forEach(value -> addDistinctLimited(draftRisks, value, 8));

        List<String> draftRecommendations = new ArrayList<>(stringItems(recommendations));
        actionItems.stream()
                .map(action -> nestedString(action, "action"))
                .forEach(value -> addDistinctLimited(draftRecommendations, value, 8));

        List<String> draftEvidence = new ArrayList<>(stringItems(evidence));
        evidenceMap.stream()
                .map(mapping -> "证据映射：" + nestedString(mapping, "claim") + "（" + nestedString(mapping, "supportLevel") + "）")
                .forEach(value -> addDistinctLimited(draftEvidence, value, 10));

        return mapOf(
                "title", title,
                "summary", summary,
                "highlights", draftHighlights,
                "risks", draftRisks,
                "recommendations", draftRecommendations,
                "evidence", draftEvidence
        );
    }

    private void addDistinctLimited(List<String> values, String value, int limit) {
        if (!StringUtils.hasText(value) || values.size() >= limit) {
            return;
        }
        String normalized = value.trim();
        if (!values.contains(normalized)) {
            values.add(normalized);
        }
    }

    private AgentResult runReportAnalystLegacy(AgentContext context) {
        Collection<?> highlights = asCollection(context.task().input().get("highlights"));
        Collection<?> risks = asCollection(context.task().input().get("risks"));
        Collection<?> recommendations = asCollection(context.task().input().get("recommendations"));
        Collection<?> evidence = asCollection(context.task().input().get("evidence"));
        boolean hasRisks = !risks.isEmpty();
        return AgentResult.success(
                "Report Analyst Agent",
                "报告分析",
                "已整理科研报告重点发现、风险和建议",
                mapOf(
                        "title", context.task().input().get("title"),
                        "highlightCount", highlights.size(),
                        "riskCount", risks.size(),
                        "recommendationCount", recommendations.size(),
                        "evidenceCount", evidence.size()
                ),
                List.of(mapOf(
                        "type", "REPORT",
                        "title", firstNonBlank(stringValue(context.task().input().get("title")), "AI 科研报告"),
                        "description", "重点发现 " + highlights.size() + " 条，建议 " + recommendations.size() + " 条"
                )),
                hasRisks ? 0.78d : 0.84d,
                STATUS_VERIFIED
        );
    }

    private List<Map<String, Object>> buildReportTrendSignals(
            DashboardSummary summary,
            List<NameValuePoint> trend,
            List<EcosystemAnalyticsPoint> ecosystems
    ) {
        List<Map<String, Object>> signals = new ArrayList<>();
        long trendTotal = trend.stream().mapToLong(NameValuePoint::value).sum();
        if (trend.isEmpty()) {
            signals.add(reportSignal("TREND_MISSING", "MEDIUM", "Missing trend data", "No observation trend points are available for the selected range.", null));
        } else {
            signals.add(reportSignal("TREND_VOLUME", "LOW", "Observation trend volume", "Trend window contains " + trendTotal + " observation records.", trendTotal));
            if (trend.size() >= 2) {
                long first = trend.get(0).value();
                long last = trend.get(trend.size() - 1).value();
                double average = trend.stream().mapToLong(NameValuePoint::value).average().orElse(0.0d);
                if (average > 0.0d && last >= Math.max(first + 2, average * 1.5d)) {
                    signals.add(reportSignal("OBSERVATION_SPIKE", "MEDIUM", "Observation spike", "Latest trend point is materially higher than the window average.", last));
                } else if (average > 0.0d && last <= Math.max(0.0d, average * 0.5d) && first > last) {
                    signals.add(reportSignal("OBSERVATION_DROP", "MEDIUM", "Observation drop", "Latest trend point is materially lower than the window average.", last));
                } else {
                    signals.add(reportSignal("OBSERVATION_STABLE", "LOW", "Observation trend stable", "No large observation swing was detected in the trend window.", last));
                }
            }
        }
        ecosystems.stream()
                .max(Comparator.comparingLong(EcosystemAnalyticsPoint::observationCount))
                .ifPresent(point -> signals.add(reportSignal(
                        "TOP_ECOSYSTEM",
                        "LOW",
                        "Most active ecosystem",
                        point.ecosystemName() + " has the highest observation count in ecosystem analytics.",
                        point.observationCount()
                )));
        if (summary.totalEcosystems() > 0 && ecosystems.isEmpty()) {
            signals.add(reportSignal("ECOSYSTEM_ANALYTICS_MISSING", "MEDIUM", "Missing ecosystem analytics", "Dashboard has ecosystems but no ecosystem analytics points were returned.", summary.totalEcosystems()));
        }
        return signals;
    }

    private List<Map<String, Object>> buildReportRiskSignals(
            DashboardSummary summary,
            Collection<?> sourceRisks,
            Collection<?> sourceEvidence,
            List<AgentResult> previousResults,
            List<NameValuePoint> observers,
            List<NameValuePoint> protection
    ) {
        List<Map<String, Object>> signals = new ArrayList<>();
        int index = 1;
        for (String risk : stringItems(sourceRisks)) {
            signals.add(reportSignal("SOURCE_RISK_" + index, "MEDIUM", "Report source risk", risk, null));
            index++;
        }
        if (summary.totalSpecies() == 0 || summary.totalObservations() == 0 || summary.totalEcosystems() == 0) {
            signals.add(reportSignal("DATA_EMPTY", "HIGH", "Insufficient system data", "Core dashboard counts contain zero values, so report conclusions need manual review.", null));
        }
        if (sourceEvidence.isEmpty() && previousEvidenceTitles(previousResults).isEmpty()) {
            signals.add(reportSignal("EVIDENCE_GAP", "HIGH", "Missing report evidence", "No report evidence or previous agent evidence is available to support the draft.", null));
        }
        if (protection.stream().anyMatch(this::isUnknownProtectionBucket)) {
            signals.add(reportSignal("PROTECTION_GAP", "MEDIUM", "Protection data gap", "Protection level distribution still contains unknown or unclassified buckets.", null));
        }
        if (summary.totalObservations() > 0 && observers.size() <= 1) {
            signals.add(reportSignal("OBSERVER_CONCENTRATION", "LOW", "Observer concentration", "Observation activity is concentrated in one or fewer active observers.", observers.size()));
        }
        return signals;
    }

    private List<Map<String, Object>> buildReportActionItems(
            Collection<?> recommendations,
            List<Map<String, Object>> riskSignals,
            List<Map<String, Object>> trendSignals
    ) {
        List<Map<String, Object>> actions = new ArrayList<>();
        int index = 1;
        for (String recommendation : stringItems(recommendations)) {
            actions.add(reportAction("RECOMMENDATION_" + index, "MEDIUM", recommendation, "Draft recommendation"));
            index++;
        }
        if (hasReportSignal(riskSignals, "DATA_EMPTY")) {
            actions.add(reportAction("ACTION_COMPLETE_BASELINE_DATA", "HIGH", "Complete species, observation and ecosystem baseline data before formal publication.", "DATA_EMPTY"));
        }
        if (hasReportSignal(riskSignals, "EVIDENCE_GAP")) {
            actions.add(reportAction("ACTION_ATTACH_EVIDENCE", "HIGH", "Attach source statistics, RAG citations or observation records to every key conclusion.", "EVIDENCE_GAP"));
        }
        if (hasReportSignal(riskSignals, "PROTECTION_GAP")) {
            actions.add(reportAction("ACTION_FILL_PROTECTION_LEVELS", "MEDIUM", "Review species files with missing protection-level metadata.", "PROTECTION_GAP"));
        }
        if (hasReportSignal(trendSignals, "OBSERVATION_SPIKE") || hasReportSignal(trendSignals, "OBSERVATION_DROP")) {
            actions.add(reportAction("ACTION_REVIEW_TREND_CHANGE", "MEDIUM", "Review the observation records behind the latest trend change.", "TREND_CHANGE"));
        }
        if (actions.isEmpty()) {
            actions.add(reportAction("ACTION_MAINTAIN_REVIEW_CADENCE", "LOW", "Maintain routine expert review for key observations and report conclusions.", "DEFAULT"));
        }
        return actions;
    }

    private List<Map<String, Object>> buildReportEvidenceMap(
            Collection<?> highlights,
            Collection<?> risks,
            Collection<?> recommendations,
            Collection<?> evidence,
            List<AgentResult> previousResults,
            DashboardSummary summary
    ) {
        List<String> evidenceTitles = new ArrayList<>(stringItems(evidence));
        evidenceTitles.addAll(previousEvidenceTitles(previousResults));
        String supportLevel = evidenceTitles.isEmpty()
                ? summary.totalObservations() > 0 ? "PARTIAL_SYSTEM_STATS" : "WEAK"
                : "DIRECT";
        List<Map<String, Object>> mappings = new ArrayList<>();
        addEvidenceMappings(mappings, "HIGHLIGHT", highlights, evidenceTitles, supportLevel);
        addEvidenceMappings(mappings, "RISK", risks, evidenceTitles, supportLevel);
        addEvidenceMappings(mappings, "ACTION", recommendations, evidenceTitles, supportLevel);
        if (mappings.isEmpty()) {
            mappings.add(mapOf(
                    "claimType", "REPORT",
                    "claim", "Report draft has no explicit claims to map.",
                    "supportLevel", supportLevel,
                    "supportingEvidence", evidenceTitles.stream().limit(3).toList()
            ));
        }
        return mappings.stream().limit(12).toList();
    }

    private void addEvidenceMappings(
            List<Map<String, Object>> mappings,
            String claimType,
            Collection<?> claims,
            List<String> evidenceTitles,
            String supportLevel
    ) {
        for (String claim : stringItems(claims)) {
            mappings.add(mapOf(
                    "claimType", claimType,
                    "claim", claim,
                    "supportLevel", supportLevel,
                    "supportingEvidence", evidenceTitles.stream().limit(3).toList()
            ));
        }
    }

    private List<Map<String, Object>> reportAnalystEvidence(
            List<Map<String, Object>> trendSignals,
            List<Map<String, Object>> riskSignals,
            List<Map<String, Object>> actionItems,
            List<Map<String, Object>> evidenceMap
    ) {
        List<Map<String, Object>> evidence = new ArrayList<>();
        evidence.add(mapOf(
                "type", "REPORT_ANALYSIS",
                "title", "Structured report analysis",
                "description", "Trend signals " + trendSignals.size() + ", risk signals " + riskSignals.size() + ", action items " + actionItems.size()
        ));
        trendSignals.stream().limit(4).forEach(signal -> evidence.add(mapOf(
                "type", "REPORT_TREND",
                "title", signal.get("title"),
                "description", signal.get("message"),
                "severity", signal.get("severity"),
                "code", signal.get("code")
        )));
        riskSignals.stream().limit(6).forEach(signal -> evidence.add(mapOf(
                "type", "REPORT_RISK",
                "title", signal.get("title"),
                "description", signal.get("message"),
                "severity", signal.get("severity"),
                "code", signal.get("code")
        )));
        evidence.add(mapOf(
                "type", "REPORT_EVIDENCE_MAP",
                "title", "Claim evidence map",
                "description", "Mapped " + evidenceMap.size() + " draft claims to evidence support levels."
        ));
        return evidence;
    }

    private double reportAnalystConfidence(
            Collection<?> highlights,
            Collection<?> risks,
            Collection<?> recommendations,
            Collection<?> evidence,
            List<Map<String, Object>> trendSignals,
            List<Map<String, Object>> riskSignals
    ) {
        long highRiskCount = riskSignals.stream()
                .filter(signal -> "HIGH".equalsIgnoreCase(stringValue(signal.get("severity"))))
                .count();
        double value = 0.58d
                + Math.min(0.12d, highlights.size() * 0.025d)
                + Math.min(0.10d, recommendations.size() * 0.025d)
                + Math.min(0.12d, evidence.size() * 0.03d)
                + Math.min(0.10d, trendSignals.size() * 0.02d)
                - Math.min(0.24d, risks.size() * 0.035d)
                - Math.min(0.28d, highRiskCount * 0.14d);
        return boundedConfidence(value);
    }

    private Map<String, Object> reportSignal(String code, String severity, String title, String message, Object value) {
        return mapOf(
                "code", code,
                "severity", severity,
                "title", title,
                "message", message,
                "value", value
        );
    }

    private Map<String, Object> reportAction(String code, String priority, String action, String source) {
        return mapOf(
                "code", code,
                "priority", priority,
                "action", action,
                "source", source
        );
    }

    private boolean hasReportSignal(List<Map<String, Object>> signals, String code) {
        return signals.stream().anyMatch(signal -> code.equals(signal.get("code")));
    }

    private boolean isUnknownProtectionBucket(NameValuePoint point) {
        String name = safe(point == null ? null : point.name()).trim().toLowerCase(Locale.ROOT);
        return !StringUtils.hasText(name)
                || "unknown".equals(name)
                || "unclassified".equals(name)
                || "none".equals(name)
                || "null".equals(name);
    }

    private List<String> previousEvidenceTitles(List<AgentResult> previousResults) {
        return previousResults.stream()
                .flatMap(result -> result.evidence().stream())
                .map(evidence -> firstNonBlank(
                        nestedString(evidence, "title"),
                        nestedString(evidence, "summary"),
                        nestedString(evidence, "description")
                ))
                .filter(StringUtils::hasText)
                .distinct()
                .limit(8)
                .toList();
    }

    private AgentResult runVerifier(AgentContext context) {
        boolean hasFailure = context.previousResults().stream().anyMatch(result -> !"SUCCESS".equals(result.status()));
        List<Map<String, Object>> allEvidence = collectEvidence(context.previousResults(), false);
        List<Map<String, Object>> supportEvidence = collectEvidence(context.previousResults(), true);
        long evidenceCount = allEvidence.size();
        List<Map<String, Object>> claimChecks = buildVerificationClaimChecks(context, supportEvidence);
        long unsupportedClaimCount = claimChecks.stream().filter(this::isUnsupportedClaim).count();
        long weakClaimCount = claimChecks.stream().filter(this::isWeakClaim).count();
        boolean explicitReview = booleanValue(context.task().input().get("needsReview"))
                || booleanValue(context.task().input().get("needsHumanReview"));
        boolean previousNeedsReview = context.previousResults().stream()
                .anyMatch(result -> STATUS_NEEDS_REVIEW.equals(result.verificationStatus()));
        boolean previousInsufficient = context.previousResults().stream()
                .anyMatch(result -> STATUS_INSUFFICIENT_EVIDENCE.equals(result.verificationStatus()));
        boolean needsReview = explicitReview || hasFailure || previousNeedsReview;
        boolean insufficient = evidenceCount == 0 || previousInsufficient || unsupportedClaimCount > 0 || weakClaimCount > 0;
        String verificationStatus = needsReview
                ? STATUS_NEEDS_REVIEW
                : insufficient ? STATUS_INSUFFICIENT_EVIDENCE : STATUS_VERIFIED;
        List<Map<String, Object>> findings = buildVerificationFindings(
                hasFailure,
                explicitReview,
                previousNeedsReview,
                previousInsufficient,
                evidenceCount,
                unsupportedClaimCount,
                weakClaimCount
        );
        double confidence = verifierConfidence(context, verificationStatus, unsupportedClaimCount, weakClaimCount, findings.size());
        String summary = verifierSummary(verificationStatus, unsupportedClaimCount, weakClaimCount, findings.size());
        return AgentResult.success(
                "Verifier Agent",
                "Conclusion verification",
                summary,
                mapOf(
                        "verificationStatus", verificationStatus,
                        "evidenceCount", evidenceCount,
                        "supportEvidenceCount", supportEvidence.size(),
                        "evidenceTypes", evidenceTypeCounts(allEvidence),
                        "claimChecks", claimChecks,
                        "unsupportedClaimCount", unsupportedClaimCount,
                        "weakClaimCount", weakClaimCount,
                        "reviewFindings", findings,
                        "needsReview", needsReview,
                        "hasFailure", hasFailure,
                        "previousInsufficient", previousInsufficient
                ),
                verifierEvidence(summary, claimChecks, findings),
                confidence,
                verificationStatus
        );
    }

    private AgentResult runVerifierLegacy(AgentContext context) {
        boolean hasFailure = context.previousResults().stream().anyMatch(result -> !"SUCCESS".equals(result.status()));
        long evidenceCount = context.previousResults().stream().mapToLong(result -> result.evidence().size()).sum();
        boolean needsReview = booleanValue(context.task().input().get("needsReview"))
                || booleanValue(context.task().input().get("needsHumanReview"))
                || context.previousResults().stream().anyMatch(result -> STATUS_NEEDS_REVIEW.equals(result.verificationStatus()));
        boolean insufficient = evidenceCount == 0
                || context.previousResults().stream().anyMatch(result -> STATUS_INSUFFICIENT_EVIDENCE.equals(result.verificationStatus()));
        String verificationStatus = hasFailure || needsReview
                ? STATUS_NEEDS_REVIEW
                : insufficient ? STATUS_INSUFFICIENT_EVIDENCE : STATUS_VERIFIED;
        double confidence = boundedConfidence(numberValue(context.task().input().get("confidence"), aggregateConfidence(context.previousResults())));
        if (STATUS_NEEDS_REVIEW.equals(verificationStatus)) {
            confidence = Math.min(confidence, 0.72d);
        } else if (STATUS_INSUFFICIENT_EVIDENCE.equals(verificationStatus)) {
            confidence = Math.min(confidence, 0.64d);
        }
        String summary = switch (verificationStatus) {
            case STATUS_NEEDS_REVIEW -> "结论需要人工复核后再作为正式依据";
            case STATUS_INSUFFICIENT_EVIDENCE -> "证据不足，建议补充系统数据或知识库资料";
            default -> "结论已有系统数据或知识证据支撑";
        };
        return AgentResult.success(
                "Verifier Agent",
                "结论验证",
                summary,
                mapOf(
                        "verificationStatus", verificationStatus,
                        "evidenceCount", evidenceCount,
                        "needsReview", needsReview,
                        "hasFailure", hasFailure
                ),
                List.of(mapOf("type", "VERIFICATION", "title", "验证结论", "description", summary)),
                confidence,
                verificationStatus
        );
    }

    private List<Map<String, Object>> collectEvidence(List<AgentResult> results, boolean supportOnly) {
        List<Map<String, Object>> evidence = new ArrayList<>();
        for (AgentResult result : results) {
            for (Map<String, Object> item : result.evidence()) {
                String type = stringValue(item.get("type"));
                if (supportOnly && (type.equalsIgnoreCase("PLAN") || type.equalsIgnoreCase("VERIFICATION"))) {
                    continue;
                }
                evidence.add(item);
            }
        }
        return evidence;
    }

    private Map<String, Object> evidenceTypeCounts(List<Map<String, Object>> evidence) {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (Map<String, Object> item : evidence) {
            String type = firstNonBlank(stringValue(item.get("type")), "UNKNOWN");
            long count = counts.get(type) instanceof Number number ? number.longValue() : 0L;
            counts.put(type, count + 1L);
        }
        return counts;
    }

    private List<Map<String, Object>> buildVerificationClaimChecks(
            AgentContext context,
            List<Map<String, Object>> supportEvidence
    ) {
        List<Map<String, Object>> checks = new ArrayList<>();
        for (AgentResult result : context.previousResults()) {
            for (Object mapping : asCollection(result.output().get("evidenceMap"))) {
                String claim = nestedString(mapping, "claim");
                if (!StringUtils.hasText(claim)) {
                    continue;
                }
                String supportLevel = normalizeClaimSupport(nestedString(mapping, "supportLevel"));
                checks.add(verificationClaimCheck(
                        firstNonBlank(nestedString(mapping, "claimType"), "CLAIM"),
                        result.agentName(),
                        claim,
                        supportLevel,
                        supportTitlesFromMapping(mapping, supportEvidence, claim)
                ));
            }
        }
        if (checks.isEmpty()) {
            for (Map<String, Object> claim : taskClaimItems(context.task().input())) {
                String claimText = stringValue(claim.get("claim"));
                checks.add(verificationClaimCheck(
                        stringValue(claim.get("claimType")),
                        stringValue(claim.get("source")),
                        claimText,
                        inferClaimSupport(claimText, supportEvidence),
                        supportingEvidenceTitles(supportEvidence, claimText)
                ));
            }
        }
        return checks.stream().limit(16).toList();
    }

    private List<Map<String, Object>> taskClaimItems(Map<String, Object> input) {
        List<Map<String, Object>> claims = new ArrayList<>();
        addStringClaim(claims, "ANSWER", "task.answer", input.get("answer"));
        addStringClaim(claims, "SUMMARY", "task.summary", input.get("summary"));
        addStringClaim(claims, "SPECIES_NAME", "task.likelyChineseName", input.get("likelyChineseName"));
        addStringClaim(claims, "SPECIES_NAME", "task.likelyScientificName", input.get("likelyScientificName"));
        addCollectionClaims(claims, "HIGHLIGHT", "task.highlights", asCollection(input.get("highlights")));
        addCollectionClaims(claims, "RISK", "task.risks", asCollection(input.get("risks")));
        addCollectionClaims(claims, "ACTION", "task.recommendations", asCollection(input.get("recommendations")));
        return claims;
    }

    private void addStringClaim(List<Map<String, Object>> claims, String claimType, String source, Object value) {
        String text = stringValue(value);
        if (StringUtils.hasText(text)) {
            claims.add(mapOf("claimType", claimType, "source", source, "claim", text.trim()));
        }
    }

    private void addCollectionClaims(List<Map<String, Object>> claims, String claimType, String source, Collection<?> values) {
        int index = 1;
        for (String value : stringItems(values)) {
            claims.add(mapOf("claimType", claimType, "source", source + "[" + index + "]", "claim", value));
            index++;
        }
    }

    private Map<String, Object> verificationClaimCheck(
            String claimType,
            String source,
            String claim,
            String supportLevel,
            List<String> supportingEvidence
    ) {
        return mapOf(
                "claimType", claimType,
                "source", source,
                "claim", claim,
                "supportLevel", supportLevel,
                "verdict", claimVerdict(supportLevel),
                "supportingEvidence", supportingEvidence
        );
    }

    private String normalizeClaimSupport(String value) {
        String normalized = safe(value).trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("DIRECT")) {
            return "DIRECT";
        }
        if (normalized.contains("PARTIAL")) {
            return "PARTIAL_SYSTEM_STATS";
        }
        if (normalized.contains("WEAK")) {
            return "WEAK";
        }
        if (normalized.contains("UNSUPPORTED")) {
            return "UNSUPPORTED";
        }
        return "UNSUPPORTED";
    }

    private String inferClaimSupport(String claim, List<Map<String, Object>> supportEvidence) {
        if (supportEvidence.isEmpty()) {
            return "UNSUPPORTED";
        }
        if (directClaimSupport(claim, supportEvidence)) {
            return "DIRECT";
        }
        return "PARTIAL_SYSTEM_STATS";
    }

    private boolean directClaimSupport(String claim, List<Map<String, Object>> supportEvidence) {
        String evidenceText = supportEvidence.stream()
                .map(this::verificationEvidenceText)
                .filter(StringUtils::hasText)
                .toList()
                .toString()
                .toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(evidenceText)) {
            return false;
        }
        for (String token : meaningfulClaimTokens(claim)) {
            if (evidenceText.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String verificationEvidenceText(Map<String, Object> evidence) {
        return String.join(" ", List.of(
                stringValue(evidence.get("type")),
                stringValue(evidence.get("sourceType")),
                stringValue(evidence.get("title")),
                stringValue(evidence.get("summary")),
                stringValue(evidence.get("description")),
                stringValue(evidence.get("scientificName")),
                stringValue(evidence.get("chineseName")),
                stringValue(evidence.get("speciesNames"))
        ));
    }

    private List<String> meaningfulClaimTokens(String claim) {
        String normalized = safe(claim).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        return List.of(normalized.split("\\s+")).stream()
                .map(String::trim)
                .filter(token -> token.length() >= 3)
                .limit(12)
                .toList();
    }

    private List<String> supportTitlesFromMapping(Object mapping, List<Map<String, Object>> supportEvidence, String claim) {
        List<String> titles = stringItems(asCollection(fieldValue(mapping, "supportingEvidence")));
        return titles.isEmpty() ? supportingEvidenceTitles(supportEvidence, claim) : titles.stream().limit(3).toList();
    }

    private List<String> supportingEvidenceTitles(List<Map<String, Object>> supportEvidence, String claim) {
        if (supportEvidence.isEmpty()) {
            return List.of();
        }
        List<String> direct = supportEvidence.stream()
                .filter(evidence -> directClaimSupport(claim, List.of(evidence)))
                .map(evidence -> firstNonBlank(
                        stringValue(evidence.get("title")),
                        stringValue(evidence.get("summary")),
                        stringValue(evidence.get("description")),
                        stringValue(evidence.get("type"))
                ))
                .filter(StringUtils::hasText)
                .distinct()
                .limit(3)
                .toList();
        if (!direct.isEmpty()) {
            return direct;
        }
        return supportEvidence.stream()
                .map(evidence -> firstNonBlank(
                        stringValue(evidence.get("title")),
                        stringValue(evidence.get("summary")),
                        stringValue(evidence.get("description")),
                        stringValue(evidence.get("type"))
                ))
                .filter(StringUtils::hasText)
                .distinct()
                .limit(3)
                .toList();
    }

    private String claimVerdict(String supportLevel) {
        return switch (supportLevel) {
            case "DIRECT" -> "SUPPORTED";
            case "PARTIAL_SYSTEM_STATS" -> "PARTIAL";
            default -> "UNSUPPORTED";
        };
    }

    private boolean isUnsupportedClaim(Map<String, Object> claimCheck) {
        return "UNSUPPORTED".equals(claimCheck.get("supportLevel"));
    }

    private boolean isWeakClaim(Map<String, Object> claimCheck) {
        return "WEAK".equals(claimCheck.get("supportLevel"));
    }

    private List<Map<String, Object>> buildVerificationFindings(
            boolean hasFailure,
            boolean explicitReview,
            boolean previousNeedsReview,
            boolean previousInsufficient,
            long evidenceCount,
            long unsupportedClaimCount,
            long weakClaimCount
    ) {
        List<Map<String, Object>> findings = new ArrayList<>();
        addVerificationFinding(findings, hasFailure, "AGENT_FAILURE", "HIGH", "At least one agent step failed.");
        addVerificationFinding(findings, explicitReview, "EXPLICIT_REVIEW_REQUEST", "HIGH", "Workflow input explicitly requested human review.");
        addVerificationFinding(findings, previousNeedsReview, "PREVIOUS_AGENT_NEEDS_REVIEW", "HIGH", "A previous agent marked the result as requiring review.");
        addVerificationFinding(findings, previousInsufficient, "PREVIOUS_AGENT_INSUFFICIENT", "MEDIUM", "A previous agent marked evidence as insufficient.");
        addVerificationFinding(findings, evidenceCount == 0, "EVIDENCE_EMPTY", "HIGH", "No evidence was produced by the collaboration chain.");
        addVerificationFinding(findings, unsupportedClaimCount > 0, "UNSUPPORTED_CLAIMS", "HIGH", unsupportedClaimCount + " claim(s) have no supporting evidence.");
        addVerificationFinding(findings, weakClaimCount > 0, "WEAK_CLAIMS", "MEDIUM", weakClaimCount + " claim(s) only have weak support.");
        return findings;
    }

    private void addVerificationFinding(List<Map<String, Object>> findings, boolean present, String code, String severity, String message) {
        if (present) {
            findings.add(mapOf("code", code, "severity", severity, "message", message));
        }
    }

    private List<Map<String, Object>> verifierEvidence(
            String summary,
            List<Map<String, Object>> claimChecks,
            List<Map<String, Object>> findings
    ) {
        List<Map<String, Object>> evidence = new ArrayList<>();
        evidence.add(mapOf("type", "VERIFICATION", "title", "Verification conclusion", "description", summary));
        claimChecks.stream().limit(6).forEach(check -> evidence.add(mapOf(
                "type", "VERIFICATION_CLAIM_CHECK",
                "title", check.get("claimType"),
                "description", shortText(stringValue(check.get("claim")), 140),
                "supportLevel", check.get("supportLevel"),
                "verdict", check.get("verdict")
        )));
        findings.forEach(finding -> evidence.add(mapOf(
                "type", "VERIFICATION_FINDING",
                "title", finding.get("code"),
                "description", finding.get("message"),
                "severity", finding.get("severity")
        )));
        return evidence;
    }

    private double verifierConfidence(
            AgentContext context,
            String verificationStatus,
            long unsupportedClaimCount,
            long weakClaimCount,
            int findingCount
    ) {
        double confidence = boundedConfidence(numberValue(context.task().input().get("confidence"), aggregateConfidence(context.previousResults())));
        confidence -= Math.min(0.30d, unsupportedClaimCount * 0.12d);
        confidence -= Math.min(0.18d, weakClaimCount * 0.06d);
        confidence -= Math.min(0.16d, findingCount * 0.03d);
        if (STATUS_NEEDS_REVIEW.equals(verificationStatus)) {
            confidence = Math.min(confidence, 0.72d);
        } else if (STATUS_INSUFFICIENT_EVIDENCE.equals(verificationStatus)) {
            confidence = Math.min(confidence, 0.64d);
        }
        return boundedConfidence(confidence);
    }

    private String verifierSummary(String verificationStatus, long unsupportedClaimCount, long weakClaimCount, int findingCount) {
        if (STATUS_NEEDS_REVIEW.equals(verificationStatus)) {
            return "Conclusion requires human review before it is used as formal evidence.";
        }
        if (STATUS_INSUFFICIENT_EVIDENCE.equals(verificationStatus)) {
            return "Evidence is insufficient for " + unsupportedClaimCount + " unsupported and " + weakClaimCount + " weak claim(s).";
        }
        return findingCount == 0
                ? "Conclusion is supported by available system or knowledge evidence."
                : "Conclusion is supported, with " + findingCount + " non-blocking verification finding(s).";
    }

    private String shortText(String value, int maxLength) {
        return truncate(value, maxLength);
    }

    private List<TaxonomyName> extractCandidateNames(Map<String, Object> input, Collection<?> candidates) {
        Map<String, TaxonomyName> names = new LinkedHashMap<>();
        TaxonomyName likely = new TaxonomyName(
                stringValue(input.get("likelyChineseName")),
                stringValue(input.get("likelyScientificName")),
                numberValue(input.get("confidence"), 0.0d)
        );
        addTaxonomyName(names, likely);
        for (Object candidate : candidates) {
            addTaxonomyName(names, toTaxonomyName(candidate));
        }
        return new ArrayList<>(names.values());
    }

    private TaxonomyName toTaxonomyName(Object value) {
        return new TaxonomyName(
                nestedString(value, "chineseName"),
                nestedString(value, "scientificName"),
                fieldNumber(value, "confidence", 0.0d)
        );
    }

    private void addTaxonomyName(Map<String, TaxonomyName> names, TaxonomyName name) {
        if (!name.hasAnyName()) {
            return;
        }
        names.putIfAbsent(name.key(), name);
    }

    private List<SpeciesRow> lookupTaxonomySpecies(List<TaxonomyName> candidates, List<TaxonomyName> related) {
        Map<Long, SpeciesRow> results = new LinkedHashMap<>();
        List<TaxonomyName> lookupNames = new ArrayList<>();
        lookupNames.addAll(candidates);
        lookupNames.addAll(related);
        for (TaxonomyName name : lookupNames) {
            String keyword = firstNonBlank(name.scientificName(), name.chineseName());
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            try {
                for (SpeciesRow row : emptyIfNull(speciesMapper.findPage(keyword, 1, null, null, null, List.of(), 3, 0))) {
                    results.putIfAbsent(row.id(), row);
                }
            } catch (RuntimeException ignored) {
                return new ArrayList<>(results.values());
            }
            if (results.size() >= 8) {
                break;
            }
        }
        return new ArrayList<>(results.values());
    }

    private List<Map<String, Object>> findCandidateNameIssues(
            Collection<?> rawCandidates,
            List<TaxonomyName> candidates,
            List<TaxonomyName> related,
            List<SpeciesRow> systemMatches
    ) {
        List<Map<String, Object>> issues = new ArrayList<>();
        long unnamedCandidates = rawCandidates.stream()
                .map(this::toTaxonomyName)
                .filter(name -> !name.hasAnyName())
                .count();
        if (unnamedCandidates > 0) {
            issues.add(issue("MEDIUM", "候选缺少分类命名", "有 " + unnamedCandidates + " 个识别候选未提供中文名或学名"));
        }
        for (TaxonomyName candidate : candidates) {
            boolean matchedRelated = related.stream().anyMatch(record -> taxonomyNameMatches(candidate, record));
            boolean matchedSystem = systemMatches.stream().anyMatch(row -> taxonomyNameMatches(candidate, toTaxonomyName(row)));
            if (!matchedRelated && !matchedSystem) {
                issues.add(issue(
                        candidate.confidence() >= 0.72d ? "HIGH" : "MEDIUM",
                        "系统内无匹配物种档案",
                        "候选“" + candidate.displayName() + "”未匹配到系统物种档案，需补充或人工确认"
                ));
            }
            if (candidate.confidence() >= 0.8d && !StringUtils.hasText(candidate.scientificName())) {
                issues.add(issue("MEDIUM", "高置信候选缺少学名", "候选“" + candidate.displayName() + "”置信度较高但缺少学名"));
            }
        }
        return issues;
    }

    private List<Map<String, Object>> findProtectionConflicts(Collection<?> related, List<SpeciesRow> systemMatches) {
        Map<String, List<String>> protectionByName = new LinkedHashMap<>();
        Map<String, List<String>> iucnByName = new LinkedHashMap<>();
        Map<String, List<String>> chineseByScientificName = new LinkedHashMap<>();
        for (Object item : related) {
            TaxonomyName name = toTaxonomyName(item);
            addProtectionFacts(protectionByName, iucnByName, chineseByScientificName, name,
                    nestedString(item, "protectionLevel"),
                    nestedString(item, "iucnStatus"));
        }
        for (SpeciesRow row : systemMatches) {
            addProtectionFacts(protectionByName, iucnByName, chineseByScientificName, toTaxonomyName(row),
                    row.protectionLevel(),
                    row.iucnStatus());
        }
        List<Map<String, Object>> issues = new ArrayList<>();
        addStatusConflictIssues(issues, protectionByName, "保护等级冲突", "protectionLevel");
        addStatusConflictIssues(issues, iucnByName, "IUCN 状态冲突", "iucnStatus");
        for (Map.Entry<String, List<String>> entry : chineseByScientificName.entrySet()) {
            List<String> distinctNames = distinctNonBlank(entry.getValue());
            if (distinctNames.size() > 1) {
                issues.add(issue("MEDIUM", "中文名存在多种写法", "学名“" + entry.getKey() + "”对应多个中文名：" + String.join("、", distinctNames)));
            }
        }
        return issues;
    }

    private void addProtectionFacts(
            Map<String, List<String>> protectionByName,
            Map<String, List<String>> iucnByName,
            Map<String, List<String>> chineseByScientificName,
            TaxonomyName name,
            String protectionLevel,
            String iucnStatus
    ) {
        if (!name.hasAnyName()) {
            return;
        }
        String key = name.key();
        String protection = normalizeProtectionLevel(protectionLevel);
        String iucn = normalizeIucnStatus(iucnStatus);
        if (StringUtils.hasText(protection)) {
            protectionByName.computeIfAbsent(key, ignored -> new ArrayList<>()).add(protection);
        }
        if (StringUtils.hasText(iucn)) {
            iucnByName.computeIfAbsent(key, ignored -> new ArrayList<>()).add(iucn);
        }
        if (StringUtils.hasText(name.scientificName()) && StringUtils.hasText(name.chineseName())) {
            chineseByScientificName.computeIfAbsent(name.scientificName().trim(), ignored -> new ArrayList<>()).add(name.chineseName().trim());
        }
    }

    private void addStatusConflictIssues(List<Map<String, Object>> issues, Map<String, List<String>> valuesByName, String title, String field) {
        for (Map.Entry<String, List<String>> entry : valuesByName.entrySet()) {
            List<String> distinctValues = distinctNonBlank(entry.getValue());
            if (distinctValues.size() > 1) {
                issues.add(issue("HIGH", title, entry.getKey() + " 的 " + field + " 存在多个取值：" + String.join("、", distinctValues)));
            }
        }
    }

    private List<Map<String, Object>> findRagTaxonomyConflicts(
            Collection<?> ragEvidence,
            List<TaxonomyName> candidates,
            List<TaxonomyName> related,
            List<SpeciesRow> systemMatches
    ) {
        if (ragEvidence.isEmpty()) {
            return List.of();
        }
        List<TaxonomyName> knownNames = new ArrayList<>();
        knownNames.addAll(candidates);
        knownNames.addAll(related);
        knownNames.addAll(systemMatches.stream().map(this::toTaxonomyName).toList());
        List<String> knownNameTokens = knownNames.stream()
                .flatMap(name -> List.of(name.chineseName(), name.scientificName()).stream())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        String evidenceText = ragEvidence.stream()
                .map(this::ragEvidenceText)
                .filter(StringUtils::hasText)
                .toList()
                .toString();
        List<Map<String, Object>> issues = new ArrayList<>();
        if (!knownNameTokens.isEmpty() && knownNameTokens.stream().noneMatch(token -> containsIgnoreCase(evidenceText, token))) {
            issues.add(issue("MEDIUM", "RAG 证据未覆盖候选名", "已召回证据未直接提及候选或系统匹配物种名"));
        }
        List<String> knownIucn = knownIucnStatuses(related, systemMatches);
        List<String> evidenceIucn = extractIucnStatuses(evidenceText);
        if (!knownIucn.isEmpty() && !evidenceIucn.isEmpty() && evidenceIucn.stream().noneMatch(knownIucn::contains)) {
            issues.add(issue("HIGH", "外部 IUCN 证据冲突", "系统记录为 " + String.join("、", knownIucn) + "，外部证据提及 " + String.join("、", evidenceIucn)));
        }
        List<String> knownProtection = knownProtectionLevels(related, systemMatches);
        List<String> evidenceProtection = extractProtectionLevels(evidenceText);
        if (!knownProtection.isEmpty() && !evidenceProtection.isEmpty() && evidenceProtection.stream().noneMatch(knownProtection::contains)) {
            issues.add(issue("HIGH", "外部保护等级证据冲突", "系统记录为 " + String.join("、", knownProtection) + "，外部证据提及 " + String.join("、", evidenceProtection)));
        }
        return issues;
    }

    private String ragEvidenceText(Object evidence) {
        return String.join(" ", List.of(
                nestedString(evidence, "sourceType"),
                nestedString(evidence, "sourceName"),
                nestedString(evidence, "title"),
                nestedString(evidence, "summary"),
                nestedString(evidence, "contentSnippet"),
                nestedString(evidence, "content")
        ));
    }

    private List<String> knownIucnStatuses(Collection<?> related, List<SpeciesRow> systemMatches) {
        List<String> values = new ArrayList<>();
        for (Object item : related) {
            values.add(normalizeIucnStatus(nestedString(item, "iucnStatus")));
        }
        for (SpeciesRow row : systemMatches) {
            values.add(normalizeIucnStatus(row.iucnStatus()));
        }
        return distinctNonBlank(values);
    }

    private List<String> knownProtectionLevels(Collection<?> related, List<SpeciesRow> systemMatches) {
        List<String> values = new ArrayList<>();
        for (Object item : related) {
            values.add(normalizeProtectionLevel(nestedString(item, "protectionLevel")));
        }
        for (SpeciesRow row : systemMatches) {
            values.add(normalizeProtectionLevel(row.protectionLevel()));
        }
        return distinctNonBlank(values);
    }

    private List<String> extractIucnStatuses(String text) {
        String upper = safe(text).toUpperCase(Locale.ROOT);
        List<String> values = new ArrayList<>();
        boolean criticallyEndangered = containsAny(text, List.of("极危", "CRITICALLY ENDANGERED"));
        boolean endangered = containsAny(text, List.of("濒危"))
                || (containsAny(text, List.of("ENDANGERED")) && !criticallyEndangered);
        addIfPresent(values, containsToken(upper, "CR") || criticallyEndangered, "CR");
        addIfPresent(values, containsToken(upper, "EN") || endangered, "EN");
        addIfPresent(values, containsToken(upper, "VU") || containsAny(text, List.of("易危", "VULNERABLE")), "VU");
        addIfPresent(values, containsToken(upper, "NT") || containsAny(text, List.of("近危", "NEAR THREATENED")), "NT");
        addIfPresent(values, containsToken(upper, "LC") || containsAny(text, List.of("无危", "LEAST CONCERN")), "LC");
        return distinctNonBlank(values);
    }

    private List<String> extractProtectionLevels(String text) {
        List<String> values = new ArrayList<>();
        addIfPresent(values, containsAny(text, List.of("国家一级", "一级保护", "I级保护")), "国家一级");
        addIfPresent(values, containsAny(text, List.of("国家二级", "二级保护", "II级保护")), "国家二级");
        return distinctNonBlank(values);
    }

    private String normalizeIucnStatus(String value) {
        String text = safe(value).toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (containsToken(text, "CR") || text.contains("极危") || text.contains("CRITICALLY ENDANGERED")) return "CR";
        if (containsToken(text, "EN") || text.contains("濒危") || text.contains("ENDANGERED")) return "EN";
        if (containsToken(text, "VU") || text.contains("易危") || text.contains("VULNERABLE")) return "VU";
        if (containsToken(text, "NT") || text.contains("近危") || text.contains("NEAR THREATENED")) return "NT";
        if (containsToken(text, "LC") || text.contains("无危") || text.contains("LEAST CONCERN")) return "LC";
        return value.trim();
    }

    private String normalizeProtectionLevel(String value) {
        String text = safe(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (containsAny(text, List.of("国家一级", "一级保护", "I级保护"))) return "国家一级";
        if (containsAny(text, List.of("国家二级", "二级保护", "II级保护"))) return "国家二级";
        return text.trim();
    }

    private Map<String, Object> toTaxonomySpeciesFact(SpeciesRow row) {
        return mapOf(
                "id", row.id(),
                "chineseName", row.chineseName(),
                "scientificName", row.scientificName(),
                "rank", row.rank(),
                "protectionLevel", row.protectionLevel(),
                "iucnStatus", row.iucnStatus(),
                "sourcePath", "/species/" + row.id()
        );
    }

    private TaxonomyName toTaxonomyName(SpeciesRow row) {
        return new TaxonomyName(row.chineseName(), row.scientificName(), 0.0d);
    }

    private boolean taxonomyNameMatches(TaxonomyName left, TaxonomyName right) {
        return left.hasAnyName()
                && right.hasAnyName()
                && (
                sameName(left.scientificName(), right.scientificName())
                        || sameName(left.chineseName(), right.chineseName())
                        || containsName(left.scientificName(), right.scientificName())
                        || containsName(left.chineseName(), right.chineseName())
        );
    }

    private boolean sameName(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && normalizeNameToken(left).equals(normalizeNameToken(right));
    }

    private boolean containsName(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        String normalizedLeft = normalizeNameToken(left);
        String normalizedRight = normalizeNameToken(right);
        return normalizedLeft.length() >= 3
                && normalizedRight.length() >= 3
                && (normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft));
    }

    private double taxonomyConfidence(
            int candidateCount,
            int relatedCount,
            int speciesItemCount,
            int systemLookupCount,
            int ragEvidenceCount,
            int issueCount,
            boolean hasWarnings
    ) {
        double value = 0.58d
                + Math.min(0.16d, candidateCount * 0.03d)
                + Math.min(0.16d, (relatedCount + systemLookupCount) * 0.04d)
                + Math.min(0.08d, speciesItemCount * 0.03d)
                + Math.min(0.07d, ragEvidenceCount * 0.015d)
                - Math.min(0.32d, issueCount * 0.08d)
                - (hasWarnings ? 0.12d : 0.0d);
        return boundedConfidence(value);
    }

    private Map<String, Object> issue(String severity, String title, String message) {
        return mapOf("severity", severity, "title", title, "message", message);
    }

    private SystemDataQuery extractSystemDataQuery(AgentTask task) {
        Object structuredQuery = task.input().get("structuredQuery");
        return new SystemDataQuery(
                firstNonBlank(
                        stringValue(task.input().get("speciesKeyword")),
                        nestedString(structuredQuery, "speciesKeyword"),
                        stringValue(task.input().get("likelyChineseName")),
                        stringValue(task.input().get("likelyScientificName"))
                ),
                firstNonBlank(
                        stringValue(task.input().get("ecosystemKeyword")),
                        nestedString(structuredQuery, "ecosystemKeyword")
                ),
                firstNonBlank(
                        stringValue(task.input().get("locationKeyword")),
                        nestedString(structuredQuery, "locationKeyword")
                ),
                firstNonBlank(
                        stringValue(task.input().get("protectionLevel")),
                        nestedString(structuredQuery, "protectionLevel")
                ),
                firstNonBlank(
                        stringValue(task.input().get("iucnStatus")),
                        nestedString(structuredQuery, "iucnStatus")
                )
        );
    }

    private Map<String, Object> toSpeciesFact(SpeciesRow row) {
        String displayName = firstNonBlank(row.chineseName(), row.scientificName(), "物种 #" + row.id());
        return mapOf(
                "type", "SYSTEM_SPECIES",
                "sourceType", "SPECIES",
                "sourceId", row.id(),
                "title", displayName,
                "scientificName", row.scientificName(),
                "chineseName", row.chineseName(),
                "rank", row.rank(),
                "protectionLevel", row.protectionLevel(),
                "iucnStatus", row.iucnStatus(),
                "sourcePath", "/species/" + row.id(),
                "description", "系统物种档案：" + displayName
                        + "，保护等级 " + firstNonBlank(row.protectionLevel(), "未标注")
                        + "，IUCN " + firstNonBlank(row.iucnStatus(), "未标注")
                        + "，分布 " + firstNonBlank(row.geoRangeText(), row.distribution(), "未标注")
        );
    }

    private Map<String, Object> toObservationFact(ObservationView row) {
        return toObservationFact(row, List.of());
    }

    private Map<String, Object> toObservationFact(ObservationView row, List<ObservationSpeciesView> speciesItems) {
        String title = "观测 #" + row.id() + " " + firstNonBlank(row.locationName(), row.ecosystemName(), "");
        List<String> speciesNames = speciesItems.stream()
                .map(item -> firstNonBlank(item.chineseName(), item.scientificName()))
                .filter(StringUtils::hasText)
                .toList();
        return mapOf(
                "type", "SYSTEM_OBSERVATION",
                "sourceType", "OBSERVATION",
                "sourceId", row.id(),
                "title", title,
                "ecosystemName", row.ecosystemName(),
                "locationName", row.locationName(),
                "observedAt", row.observedAt() == null ? null : row.observedAt().toString(),
                "lat", row.locationLat(),
                "lng", row.locationLng(),
                "speciesNames", speciesNames,
                "sourcePath", "/observations",
                "description", "系统观测记录："
                        + firstNonBlank(row.locationName(), row.ecosystemName(), "未标注地点")
                        + "，时间 " + (row.observedAt() == null ? "未标注" : row.observedAt())
                        + (speciesNames.isEmpty() ? "" : "，关联物种 " + String.join("、", speciesNames))
        );
    }

    private Map<String, Object> toEcosystemFact(Ecosystem ecosystem) {
        return mapOf(
                "type", "SYSTEM_ECOSYSTEM",
                "sourceType", "ECOSYSTEM",
                "sourceId", ecosystem.getId(),
                "title", ecosystem.getName(),
                "ecosystemType", ecosystem.getType(),
                "sourcePath", "/ecosystems",
                "description", "系统生态系统：" + ecosystem.getName()
                        + "，类型 " + firstNonBlank(ecosystem.getType(), "未标注")
                        + "，说明 " + firstNonBlank(truncate(ecosystem.getDescription(), 120), "未填写")
        );
    }

    private boolean isSubject(AgentTask task, String subjectType) {
        return task.subjectId() != null
                && StringUtils.hasText(task.subjectType())
                && subjectType.equalsIgnoreCase(task.subjectType());
    }

    private AgentRun requireRun(Long id) {
        AgentRun run = agentRunMapper.findRunById(id);
        if (run == null) {
            throw new NotFoundException("Agent 协作轨迹不存在");
        }
        return run;
    }

    private AgentDtos.AgentRunReplayView buildReplayView(AgentDtos.AgentRunView run) {
        List<AgentDtos.AgentStepView> steps = run.steps() == null ? List.of() : run.steps();
        List<String> agentSequence = steps.stream()
                .map(AgentDtos.AgentStepView::agentName)
                .filter(StringUtils::hasText)
                .toList();
        long evidenceCount = steps.stream()
                .map(AgentDtos.AgentStepView::evidence)
                .mapToLong(evidence -> asCollection(evidence).size())
                .sum();
        AgentDtos.AgentStepView verifierStep = steps.stream()
                .filter(step -> "Verifier Agent".equals(step.agentName()))
                .reduce((first, second) -> second)
                .orElse(null);
        Object verifierOutput = verifierStep == null ? null : verifierStep.output();
        Object claimChecks = fieldValue(verifierOutput, "claimChecks");
        Object reviewFindings = fieldValue(verifierOutput, "reviewFindings");
        Map<String, Object> reconstructedFinalOutput = mapOf(
                "workflowType", run.workflowType(),
                "status", run.status(),
                "verificationStatus", run.verificationStatus(),
                "confidence", run.confidence(),
                "summary", run.summary(),
                "agents", agentSequence,
                "evidenceCount", evidenceCount,
                "verifierOutput", verifierOutput,
                "claimChecks", claimChecks,
                "reviewFindings", reviewFindings
        );
        List<String> issues = replayConsistencyIssues(run, steps, agentSequence, verifierStep, verifierOutput, evidenceCount);
        boolean reconstructable = run.finalOutput() != null && !steps.isEmpty() && verifierStep != null;
        String replayStatus = reconstructable
                ? issues.isEmpty() ? "READY" : "READY_WITH_WARNINGS"
                : "INCOMPLETE";
        return new AgentDtos.AgentRunReplayView(
                run.id(),
                replayStatus,
                reconstructable,
                run,
                steps.size(),
                evidenceCount,
                agentSequence,
                reconstructedFinalOutput,
                verifierOutput,
                claimChecks == null ? List.of() : claimChecks,
                reviewFindings == null ? List.of() : reviewFindings,
                issues
        );
    }

    private List<String> replayConsistencyIssues(
            AgentDtos.AgentRunView run,
            List<AgentDtos.AgentStepView> steps,
            List<String> agentSequence,
            AgentDtos.AgentStepView verifierStep,
            Object verifierOutput,
            long evidenceCount
    ) {
        List<String> issues = new ArrayList<>();
        if (run.finalOutput() == null) {
            issues.add("RUN_FINAL_OUTPUT_MISSING");
        }
        if (steps.isEmpty()) {
            issues.add("RUN_STEPS_MISSING");
        }
        if (verifierStep == null) {
            issues.add("VERIFIER_STEP_MISSING");
        }
        if (evidenceCount == 0) {
            issues.add("STEP_EVIDENCE_EMPTY");
        }
        if (!stepsAreSequential(steps)) {
            issues.add("STEP_ORDER_NOT_SEQUENTIAL");
        }
        String finalVerification = nestedString(run.finalOutput(), "verificationStatus");
        if (StringUtils.hasText(finalVerification) && !Objects.equals(finalVerification, run.verificationStatus())) {
            issues.add("FINAL_OUTPUT_VERIFICATION_MISMATCH");
        }
        String verifierVerification = nestedString(verifierOutput, "verificationStatus");
        if (StringUtils.hasText(verifierVerification) && !Objects.equals(verifierVerification, run.verificationStatus())) {
            issues.add("VERIFIER_OUTPUT_VERIFICATION_MISMATCH");
        }
        List<String> finalAgents = stringItems(asCollection(fieldValue(run.finalOutput(), "agents")));
        if (!finalAgents.isEmpty() && !finalAgents.equals(agentSequence)) {
            issues.add("FINAL_OUTPUT_AGENT_SEQUENCE_MISMATCH");
        }
        return issues;
    }

    private boolean stepsAreSequential(List<AgentDtos.AgentStepView> steps) {
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).stepOrder() != index + 1) {
                return false;
            }
        }
        return true;
    }

    private AgentDtos.AgentRunView toRunView(AgentRun run, List<AgentStep> steps) {
        return new AgentDtos.AgentRunView(
                run.getId(),
                run.getWorkflowType(),
                run.getStatus(),
                run.getSubjectType(),
                run.getSubjectId(),
                run.getUserId(),
                run.getUsername(),
                run.getPrompt(),
                run.getSummary(),
                run.getVerificationStatus(),
                run.getConfidence() == null ? null : run.getConfidence().doubleValue(),
                readJson(run.getFinalOutputJson()),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getCreatedAt(),
                steps.stream().map(this::toStepView).toList()
        );
    }

    private AgentDtos.AgentStepView toStepView(AgentStep step) {
        return new AgentDtos.AgentStepView(
                step.getId(),
                step.getRunId(),
                step.getStepOrder() == null ? 0 : step.getStepOrder(),
                step.getAgentName(),
                step.getAgentRole(),
                step.getStatus(),
                step.getSummary(),
                readJson(step.getInputJson()),
                readJson(step.getOutputJson()),
                readJson(step.getEvidenceJson()),
                step.getErrorMessage(),
                step.getConfidence() == null ? null : step.getConfidence().doubleValue(),
                step.getDurationMs(),
                step.getStartedAt(),
                step.getFinishedAt()
        );
    }

    private boolean canViewAllRuns(CurrentUser currentUser) {
        return currentUser.authorities().contains("ROLE_ADMIN")
                || currentUser.authorities().contains("AUDIT_READ")
                || currentUser.authorities().contains("REPORT_READ")
                || currentUser.authorities().contains("AI_REVIEW_READ")
                || currentUser.authorities().contains("RAG_READ");
    }

    private List<String> inferAgentNames(String workflowType) {
        String normalized = firstNonBlank(workflowType, WORKFLOW_ASSISTANT_CHAT).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case WORKFLOW_SPECIES_IDENTIFY -> List.of("Coordinator", "Vision Review", "Taxonomy", "RAG Evidence", "Verifier");
            case WORKFLOW_SPECIES_PROFILE_ASSIST -> List.of("Coordinator", "RAG Evidence", "Taxonomy", "Verifier");
            case WORKFLOW_OBSERVATION_QA -> List.of("Coordinator", "Observation QA", "Taxonomy", "System Data", "Verifier");
            case WORKFLOW_RESEARCH_REPORT -> List.of("Coordinator", "System Data", "RAG Evidence", "Report Analyst", "Verifier");
            case WORKFLOW_KNOWLEDGE_GOVERNANCE -> List.of("Coordinator", "RAG Evidence", "Taxonomy", "Verifier");
            default -> List.of("Coordinator", "System Data", "RAG Evidence", "Verifier");
        };
    }

    private String resolveScenario(AgentTask task) {
        if (StringUtils.hasText(task.scenario())) {
            return task.scenario();
        }
        String workflowType = firstNonBlank(task.workflowType(), WORKFLOW_ASSISTANT_CHAT).toUpperCase(Locale.ROOT);
        return switch (workflowType) {
            case WORKFLOW_SPECIES_IDENTIFY -> RagKnowledgeService.SCENARIO_IMAGE_IDENTIFICATION;
            case WORKFLOW_SPECIES_PROFILE_ASSIST -> RagKnowledgeService.SCENARIO_SPECIES_PROFILE;
            case WORKFLOW_OBSERVATION_QA -> RagKnowledgeService.SCENARIO_OBSERVATION_ANALYSIS;
            case WORKFLOW_RESEARCH_REPORT -> RagKnowledgeService.SCENARIO_REPORT;
            default -> RagKnowledgeService.SCENARIO_ASSISTANT;
        };
    }

    private Map<String, Object> toEvidenceMap(RagSearchHit hit) {
        return mapOf(
                "type", "RAG",
                "sourceType", hit.sourceType(),
                "sourceId", hit.sourceId(),
                "documentId", hit.documentId(),
                "chunkId", hit.chunkId(),
                "title", hit.title(),
                "summary", firstNonBlank(hit.summary(), truncate(hit.content(), 160)),
                "score", hit.score(),
                "sourcePath", hit.sourcePath()
        );
    }

    private String ragEvidenceSummary(int hitCount, int governanceIssueCount, boolean hasGovernanceEvidence) {
        if (hasGovernanceEvidence) {
            return "发现 " + governanceIssueCount + " 项知识库治理问题，并召回 " + hitCount + " 条知识证据";
        }
        return hitCount == 0 ? "未召回强相关知识证据" : "召回 " + hitCount + " 条知识证据";
    }

    private List<Map<String, Object>> buildKnowledgeGovernanceEvidence(AgentTask task) {
        if (!isKnowledgeGovernanceWorkflow(task)) {
            return List.of();
        }
        List<Map<String, Object>> evidence = new ArrayList<>();
        int limit = 12;
        int count = 0;
        for (Object issue : asCollection(task.input().get("failedDocuments"))) {
            if (count++ >= limit) {
                break;
            }
            evidence.add(governanceDocumentEvidence(
                    issue,
                    "RAG_GOVERNANCE_FAILED_DOCUMENT",
                    "索引失败文档",
                    "知识文档索引失败或存在错误信息"
            ));
        }
        count = 0;
        for (Object issue : asCollection(task.input().get("emptyChunkDocuments"))) {
            if (count++ >= limit) {
                break;
            }
            evidence.add(governanceDocumentEvidence(
                    issue,
                    "RAG_GOVERNANCE_EMPTY_CHUNKS",
                    "空分块文档",
                    "知识文档处于 READY 状态但没有可检索分块"
            ));
        }
        count = 0;
        for (Object group : asCollection(task.input().get("duplicateGroups"))) {
            if (count++ >= limit) {
                break;
            }
            evidence.add(governanceDuplicateEvidence(group));
        }
        return evidence;
    }

    private Map<String, Object> governanceDocumentEvidence(
            Object issue,
            String evidenceType,
            String fallbackTitle,
            String fallbackReason
    ) {
        String title = firstNonBlank(nestedString(issue, "title"), fallbackTitle);
        String reason = firstNonBlank(nestedString(issue, "reason"), fallbackReason);
        String errorMessage = nestedString(issue, "errorMessage");
        String description = StringUtils.hasText(errorMessage) ? reason + "；错误：" + errorMessage : reason;
        return mapOf(
                "type", evidenceType,
                "issueType", firstNonBlank(nestedString(issue, "issueType"), evidenceType),
                "title", fallbackTitle + "：" + title,
                "summary", reason,
                "description", description,
                "documentId", fieldValue(issue, "documentId"),
                "sourceType", fieldValue(issue, "sourceType"),
                "sourceId", fieldValue(issue, "sourceId"),
                "status", fieldValue(issue, "status"),
                "chunkCount", fieldValue(issue, "chunkCount"),
                "sourcePath", ragDocumentPath(fieldValue(issue, "documentId"))
        );
    }

    private Map<String, Object> governanceDuplicateEvidence(Object group) {
        String title = firstNonBlank(nestedString(group, "title"), "疑似重复知识");
        int documentCount = integerValue(fieldValue(group, "documentCount")) == null
                ? asCollection(fieldValue(group, "documents")).size()
                : integerValue(fieldValue(group, "documentCount"));
        String reason = firstNonBlank(
                nestedString(group, "reason"),
                "多个知识文档标题高度一致，建议检查是否重复入库"
        );
        return mapOf(
                "type", "RAG_GOVERNANCE_DUPLICATE_TITLE",
                "issueType", "DUPLICATE_TITLE",
                "title", "疑似重复标题：" + title,
                "summary", "发现 " + documentCount + " 份疑似重复文档",
                "description", reason,
                "documentCount", documentCount,
                "documents", fieldValue(group, "documents"),
                "sourcePath", "/rag-knowledge"
        );
    }

    private List<Map<String, Object>> buildKnowledgeGovernanceEvidenceMap(
            AgentTask task,
            List<Map<String, Object>> governanceEvidence,
            int governanceIssueCount
    ) {
        if (!isKnowledgeGovernanceWorkflow(task) || governanceEvidence.isEmpty()) {
            return List.of();
        }
        List<String> supportingEvidence = governanceEvidence.stream()
                .map(evidence -> firstNonBlank(
                        stringValue(evidence.get("title")),
                        stringValue(evidence.get("summary")),
                        stringValue(evidence.get("type"))
                ))
                .filter(StringUtils::hasText)
                .distinct()
                .limit(5)
                .toList();
        return List.of(mapOf(
                "claimType", "KNOWLEDGE_GOVERNANCE",
                "source", "RAG Evidence Agent",
                "claim", "知识库治理扫描发现 " + governanceIssueCount + " 项可复核问题",
                "supportLevel", "DIRECT",
                "supportingEvidence", supportingEvidence
        ));
    }

    private int knowledgeGovernanceIssueCount(AgentTask task) {
        if (!isKnowledgeGovernanceWorkflow(task)) {
            return 0;
        }
        return asCollection(task.input().get("failedDocuments")).size()
                + asCollection(task.input().get("emptyChunkDocuments")).size()
                + asCollection(task.input().get("duplicateGroups")).size();
    }

    private boolean isKnowledgeGovernanceWorkflow(AgentTask task) {
        return isKnowledgeGovernanceWorkflow(task, task == null ? null : task.workflowType());
    }

    private boolean isKnowledgeGovernanceWorkflow(AgentTask task, String workflowType) {
        String workflow = firstNonBlank(workflowType, task == null ? null : task.workflowType()).toUpperCase(Locale.ROOT);
        return WORKFLOW_KNOWLEDGE_GOVERNANCE.equals(workflow);
    }

    private String ragDocumentPath(Object documentId) {
        String value = stringValue(documentId);
        return StringUtils.hasText(value) ? "/rag-knowledge?document=" + value : null;
    }

    private String finalRunSummary(AgentTask task, AgentResult finalResult, List<AgentResult> results) {
        return firstNonBlank(finalResult.summary(), "完成 " + results.size() + " 个 agent 协作步骤");
    }

    private Map<String, Object> buildFinalOutput(
            AgentTask task,
            String workflowType,
            String status,
            String verificationStatus,
            double confidence,
            String summary,
            List<AgentResult> results,
            AgentResult finalResult
    ) {
        Map<String, Object> coordinatorOutput = outputOf(results, "Coordinator Agent");
        Map<String, Object> systemDataOutput = outputOf(results, "System Data Agent");
        Map<String, Object> ragOutput = outputOf(results, "RAG Evidence Agent");
        Map<String, Object> taxonomyOutput = outputOf(results, "Taxonomy Agent");
        Map<String, Object> observationQaOutput = outputOf(results, "Observation QA Agent");
        Map<String, Object> visionReviewOutput = outputOf(results, "Vision Review Agent");
        Map<String, Object> reportAnalystOutput = outputOf(results, "Report Analyst Agent");
        Map<String, Object> verifierOutput = outputOf(results, "Verifier Agent");
        List<Map<String, Object>> evidenceSnapshot = collectEvidence(results, true).stream()
                .limit(24)
                .toList();
        Map<String, Object> governanceOutput = buildKnowledgeGovernanceOutput(
                task,
                workflowType,
                ragOutput,
                taxonomyOutput,
                verifierOutput,
                evidenceSnapshot
        );

        return mapOf(
                "workflowType", workflowType,
                "status", status,
                "verificationStatus", verificationStatus,
                "confidence", confidence,
                "summary", summary,
                "agents", results.stream().map(AgentResult::agentName).toList(),
                "executionPlan", fieldValue(coordinatorOutput, "selectedAgents"),
                "structuredContext", mapOf(
                        "systemData", systemDataOutput,
                        "ragEvidence", ragOutput,
                        "taxonomy", taxonomyOutput,
                        "observationQa", observationQaOutput,
                        "visionReview", visionReviewOutput,
                        "reportAnalysis", reportAnalystOutput,
                        "knowledgeGovernance", governanceOutput
                ),
                "evidenceSnapshot", evidenceSnapshot,
                "riskFindings", firstNonEmpty(
                        fieldValue(governanceOutput, "governanceFindings"),
                        fieldValue(verifierOutput, "reviewFindings"),
                        fieldValue(reportAnalystOutput, "riskSignals"),
                        fieldValue(observationQaOutput, "qaIssues"),
                        fieldValue(taxonomyOutput, "issues")
                ),
                "actionItems", firstNonEmpty(
                        fieldValue(governanceOutput, "actionItems"),
                        fieldValue(reportAnalystOutput, "actionItems"),
                        fieldValue(observationQaOutput, "actionItems"),
                        fieldValue(task.input(), "recommendations")
                ),
                "reviewTicketDraft", fieldValue(visionReviewOutput, "reviewTicketDraft"),
                "reviewTaskDraft", fieldValue(observationQaOutput, "reviewTaskDraft"),
                "governanceFindings", fieldValue(governanceOutput, "governanceFindings"),
                "governanceActions", fieldValue(governanceOutput, "actionItems"),
                "governanceTaskDraft", fieldValue(governanceOutput, "governanceTaskDraft"),
                "finalAnswer", buildAgentFinalAnswer(
                        task,
                        workflowType,
                        summary,
                        finalResult,
                        systemDataOutput,
                        ragOutput,
                        visionReviewOutput,
                        reportAnalystOutput,
                        evidenceSnapshot
                ),
                "finalDraft", fieldValue(reportAnalystOutput, "draft"),
                "verifierOutput", verifierOutput
        );
    }

    private Map<String, Object> buildKnowledgeGovernanceOutput(
            AgentTask task,
            String workflowType,
            Map<String, Object> ragOutput,
            Map<String, Object> taxonomyOutput,
            Map<String, Object> verifierOutput,
            List<Map<String, Object>> evidenceSnapshot
    ) {
        if (!isKnowledgeGovernanceWorkflow(task, workflowType)) {
            return Map.of();
        }
        List<Map<String, Object>> findings = buildKnowledgeGovernanceFindings(task, taxonomyOutput);
        List<Map<String, Object>> actionItems = buildKnowledgeGovernanceActions(findings, verifierOutput);
        Map<String, Object> taskDraft = findings.isEmpty()
                ? Map.of()
                : buildKnowledgeGovernanceTaskDraft(task, findings, actionItems);
        return mapOf(
                "trigger", task.input().get("trigger"),
                "generatedAt", task.input().get("generatedAt"),
                "scannedDocumentCount", task.input().get("scannedDocumentCount"),
                "findingCount", findings.size(),
                "governanceIssueCount", fieldValue(ragOutput, "governanceIssueCount"),
                "ragHitCount", fieldValue(ragOutput, "hitCount"),
                "verificationStatus", fieldValue(verifierOutput, "verificationStatus"),
                "confidence", fieldValue(verifierOutput, "confidence"),
                "evidenceCount", evidenceSnapshot == null ? 0 : evidenceSnapshot.size(),
                "governanceFindings", findings,
                "actionItems", actionItems,
                "governanceTaskDraft", taskDraft
        );
    }

    private List<Map<String, Object>> buildKnowledgeGovernanceFindings(
            AgentTask task,
            Map<String, Object> taxonomyOutput
    ) {
        List<Map<String, Object>> findings = new ArrayList<>();
        for (Object issue : asCollection(task.input().get("failedDocuments"))) {
            findings.add(knowledgeGovernanceDocumentFinding(
                    issue,
                    "FAILED_DOCUMENT",
                    "HIGH",
                    "索引失败文档",
                    "重新解析文档并检查抽取错误信息"
            ));
        }
        for (Object issue : asCollection(task.input().get("emptyChunkDocuments"))) {
            findings.add(knowledgeGovernanceDocumentFinding(
                    issue,
                    "EMPTY_CHUNKS",
                    "MEDIUM",
                    "空分块文档",
                    "重新分块或补全文档正文，避免检索命中空知识"
            ));
        }
        for (Object group : asCollection(task.input().get("duplicateGroups"))) {
            findings.add(knowledgeGovernanceDuplicateFinding(group));
        }
        if (findings.isEmpty()) {
            int index = 1;
            for (String warning : stringItems(asCollection(task.input().get("conflictWarnings")))) {
                findings.add(mapOf(
                        "code", "GOVERNANCE_WARNING",
                        "severity", "MEDIUM",
                        "title", "知识库治理警告 " + index,
                        "message", warning
                ));
                index++;
            }
        }
        for (Object issue : asCollection(fieldValue(taxonomyOutput, "issues"))) {
            findings.add(mapOf(
                    "code", "TAXONOMY_CONFLICT",
                    "severity", firstNonBlank(nestedString(issue, "severity"), "MEDIUM"),
                    "title", firstNonBlank(nestedString(issue, "title"), "分类知识冲突"),
                    "message", firstNonBlank(nestedString(issue, "message"), "分类、命名或保护等级信息需要人工复核"),
                    "source", "Taxonomy Agent"
            ));
        }
        return findings;
    }

    private Map<String, Object> knowledgeGovernanceDocumentFinding(
            Object issue,
            String code,
            String severity,
            String fallbackTitle,
            String fallbackMessage
    ) {
        String title = firstNonBlank(nestedString(issue, "title"), fallbackTitle);
        String reason = firstNonBlank(nestedString(issue, "reason"), fallbackMessage);
        String errorMessage = nestedString(issue, "errorMessage");
        String message = StringUtils.hasText(errorMessage) ? reason + "；错误：" + errorMessage : reason;
        return mapOf(
                "code", code,
                "severity", severity,
                "title", title,
                "message", message,
                "documentId", fieldValue(issue, "documentId"),
                "sourceType", fieldValue(issue, "sourceType"),
                "sourceId", fieldValue(issue, "sourceId"),
                "status", fieldValue(issue, "status"),
                "chunkCount", fieldValue(issue, "chunkCount"),
                "sourcePath", ragDocumentPath(fieldValue(issue, "documentId"))
        );
    }

    private Map<String, Object> knowledgeGovernanceDuplicateFinding(Object group) {
        String title = firstNonBlank(nestedString(group, "title"), "疑似重复知识");
        Integer countValue = integerValue(fieldValue(group, "documentCount"));
        int documentCount = countValue == null ? asCollection(fieldValue(group, "documents")).size() : countValue;
        return mapOf(
                "code", "DUPLICATE_TITLE",
                "severity", "MEDIUM",
                "title", title,
                "message", "发现 " + documentCount + " 份疑似重复标题文档，建议合并或标记权威来源。",
                "documentCount", documentCount,
                "documents", fieldValue(group, "documents"),
                "sourcePath", "/rag-knowledge"
        );
    }

    private List<Map<String, Object>> buildKnowledgeGovernanceActions(
            List<Map<String, Object>> findings,
            Map<String, Object> verifierOutput
    ) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if (hasFinding(findings, "FAILED_DOCUMENT")) {
            actions.add(mapOf(
                    "code", "REINDEX_FAILED_DOCUMENTS",
                    "priority", "HIGH",
                    "title", "重建失败文档索引",
                    "description", "重新解析失败文档，必要时替换原始文件或记录错误原因。",
                    "targetDocumentIds", findingDocumentIds(findings, "FAILED_DOCUMENT")
            ));
        }
        if (hasFinding(findings, "EMPTY_CHUNKS")) {
            actions.add(mapOf(
                    "code", "RECHUNK_EMPTY_DOCUMENTS",
                    "priority", "MEDIUM",
                    "title", "重建空分块文档",
                    "description", "检查正文抽取结果并重新生成可检索分块。",
                    "targetDocumentIds", findingDocumentIds(findings, "EMPTY_CHUNKS")
            ));
        }
        if (hasFinding(findings, "DUPLICATE_TITLE")) {
            actions.add(mapOf(
                    "code", "MERGE_DUPLICATE_KNOWLEDGE",
                    "priority", "MEDIUM",
                    "title", "合并或标记重复知识",
                    "description", "对疑似重复标题进行人工比对，保留权威来源并归档冗余文档。",
                    "targetDocumentIds", duplicateDocumentIds(findings)
            ));
        }
        if (hasFinding(findings, "TAXONOMY_CONFLICT")) {
            actions.add(mapOf(
                    "code", "REVIEW_TAXONOMY_CONFLICTS",
                    "priority", "HIGH",
                    "title", "复核分类知识冲突",
                    "description", "核对中文名、学名、分类阶元和保护等级，确认系统档案与外部来源差异。"
            ));
        }
        if (actions.isEmpty() && STATUS_INSUFFICIENT_EVIDENCE.equals(fieldValue(verifierOutput, "verificationStatus"))) {
            actions.add(mapOf(
                    "code", "SUPPLEMENT_GOVERNANCE_EVIDENCE",
                    "priority", "MEDIUM",
                    "title", "补充治理证据",
                    "description", "本次治理任务缺少可追踪证据，建议补充文档编号、来源或冲突描述后重新运行。"
            ));
        }
        return actions;
    }

    private Map<String, Object> buildKnowledgeGovernanceTaskDraft(
            AgentTask task,
            List<Map<String, Object>> findings,
            List<Map<String, Object>> actionItems
    ) {
        String priority = findings.stream().anyMatch(item -> "HIGH".equalsIgnoreCase(stringValue(item.get("severity"))))
                ? "HIGH"
                : findings.stream().anyMatch(item -> "MEDIUM".equalsIgnoreCase(stringValue(item.get("severity")))) ? "MEDIUM" : "LOW";
        return mapOf(
                "type", "KNOWLEDGE_GOVERNANCE_REVIEW",
                "title", "知识库治理复核任务",
                "priority", priority,
                "summary", "发现 " + findings.size() + " 项知识库治理问题，建议执行 " + actionItems.size() + " 个整改动作。",
                "trigger", task.input().get("trigger"),
                "scannedDocumentCount", task.input().get("scannedDocumentCount"),
                "findingCount", findings.size(),
                "actionCount", actionItems.size(),
                "humanReviewRequired", true,
                "findings", findings.stream().limit(8).toList(),
                "actionItems", actionItems
        );
    }

    private boolean hasFinding(List<Map<String, Object>> findings, String code) {
        return findings.stream().anyMatch(item -> code.equals(stringValue(item.get("code"))));
    }

    private List<String> findingDocumentIds(List<Map<String, Object>> findings, String code) {
        return findings.stream()
                .filter(item -> code.equals(stringValue(item.get("code"))))
                .map(item -> stringValue(item.get("documentId")))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> duplicateDocumentIds(List<Map<String, Object>> findings) {
        List<String> ids = new ArrayList<>();
        findings.stream()
                .filter(item -> "DUPLICATE_TITLE".equals(stringValue(item.get("code"))))
                .flatMap(item -> asCollection(item.get("documents")).stream())
                .map(item -> stringValue(fieldValue(item, "documentId")))
                .filter(StringUtils::hasText)
                .forEach(ids::add);
        return ids.stream().distinct().toList();
    }

    private Map<String, Object> outputOf(List<AgentResult> results, String agentName) {
        return results.stream()
                .filter(result -> agentName.equals(result.agentName()))
                .reduce((first, second) -> second)
                .map(AgentResult::output)
                .orElse(Map.of());
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Object firstNonEmpty(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (isMeaningful(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isMeaningful(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof String text) {
            return StringUtils.hasText(text);
        }
        return true;
    }

    private String buildAgentFinalAnswer(
            AgentTask task,
            String workflowType,
            String summary,
            AgentResult finalResult,
            Map<String, Object> systemDataOutput,
            Map<String, Object> ragOutput,
            Map<String, Object> visionReviewOutput,
            Map<String, Object> reportAnalystOutput,
            List<Map<String, Object>> evidenceSnapshot
    ) {
        Object reportDraft = fieldValue(reportAnalystOutput, "draft");
        String reportSummary = nestedString(reportDraft, "summary");
        String speciesName = firstNonBlank(
                stringValue(task.input().get("likelyChineseName")),
                stringValue(task.input().get("likelyScientificName")),
                stringValue(visionReviewOutput.get("likelyChineseName")),
                stringValue(visionReviewOutput.get("likelyScientificName"))
        );
        String workflow = firstNonBlank(workflowType, WORKFLOW_ASSISTANT_CHAT).toUpperCase(Locale.ROOT);
        if (WORKFLOW_ASSISTANT_CHAT.equals(workflow)) {
            return buildAssistantFinalAnswer(task, summary, finalResult, systemDataOutput, ragOutput, evidenceSnapshot);
        }
        if (WORKFLOW_SPECIES_IDENTIFY.equals(workflow) && StringUtils.hasText(speciesName)) {
            return "识图候选结论：" + speciesName + "；验证结论：" + firstNonBlank(finalResult.verificationStatus(), summary);
        }
        if (WORKFLOW_RESEARCH_REPORT.equals(workflow) && StringUtils.hasText(reportSummary)) {
            return reportSummary;
        }
        return firstNonBlank(
                stringValue(task.input().get("answer")),
                stringValue(task.input().get("summary")),
                reportSummary,
                summary,
                finalResult.summary()
        );
    }

    private String buildAssistantFinalAnswer(
            AgentTask task,
            String summary,
            AgentResult finalResult,
            Map<String, Object> systemDataOutput,
            Map<String, Object> ragOutput,
            List<Map<String, Object>> evidenceSnapshot
    ) {
        String lightweightAnswer = stringValue(task.input().get("answer"));
        if (StringUtils.hasText(lightweightAnswer)) {
            return lightweightAnswer;
        }

        Collection<?> speciesMatches = asCollection(fieldValue(systemDataOutput, "speciesMatches"));
        Collection<?> observationMatches = asCollection(fieldValue(systemDataOutput, "observationMatches"));
        Collection<?> ecosystemMatches = asCollection(fieldValue(systemDataOutput, "ecosystemMatches"));
        Collection<?> ragEvidence = evidenceByType(evidenceSnapshot, "RAG");
        Integer hitCountValue = integerValue(fieldValue(ragOutput, "hitCount"));
        int hitCount = hitCountValue == null ? ragEvidence.size() : hitCountValue;

        List<String> parts = new ArrayList<>();
        String speciesNames = joinEvidenceTitles(speciesMatches, 5);
        if (StringUtils.hasText(speciesNames)) {
            parts.add("系统物种档案匹配到 " + speciesMatches.size() + " 条记录，包括 " + speciesNames + "。");
        }
        String observationNames = joinEvidenceTitles(observationMatches, 4);
        if (StringUtils.hasText(observationNames)) {
            parts.add("系统观测记录匹配到 " + observationMatches.size() + " 条，代表记录有 " + observationNames + "。");
        }
        String ecosystemNames = joinEvidenceTitles(ecosystemMatches, 4);
        if (StringUtils.hasText(ecosystemNames)) {
            parts.add("相关生态系统包括 " + ecosystemNames + "。");
        }
        String ragTitles = joinEvidenceTitles(ragEvidence, 3);
        if (StringUtils.hasText(ragTitles)) {
            parts.add("知识库召回 " + hitCount + " 条证据，主要来源是 " + ragTitles + "。");
        }

        String fallbackAnswer = stringValue(task.input().get("fallbackAnswer"));
        if (parts.isEmpty()) {
            return firstNonBlank(
                    fallbackAnswer,
                    "我还没有查到足够的系统数据或知识库证据来直接确认这个问题，建议补充物种、地点、生态系统或时间范围后再查。"
            );
        }

        String verificationNote = switch (firstNonBlank(finalResult.verificationStatus(), "")) {
            case STATUS_VERIFIED -> "这些结论已通过当前证据校验。";
            case STATUS_NEEDS_REVIEW -> "其中部分结论建议人工复核后再作为正式依据。";
            case STATUS_INSUFFICIENT_EVIDENCE -> "但当前证据还不充分，不能把它当作最终结论。";
            default -> firstNonBlank(summary, finalResult.summary());
        };
        return "我按 Agent 协作链路查了系统数据和知识库证据。" + String.join("", parts) + verificationNote;
    }

    private List<Map<String, Object>> evidenceByType(List<Map<String, Object>> evidenceSnapshot, String type) {
        if (evidenceSnapshot == null || evidenceSnapshot.isEmpty()) {
            return List.of();
        }
        return evidenceSnapshot.stream()
                .filter(item -> type.equalsIgnoreCase(stringValue(item.get("type"))))
                .toList();
    }

    private String joinEvidenceTitles(Collection<?> values, int limit) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .map(item -> firstNonBlank(
                        nestedString(item, "title"),
                        nestedString(item, "chineseName"),
                        nestedString(item, "scientificName"),
                        nestedString(item, "summary")
                ))
                .filter(StringUtils::hasText)
                .distinct()
                .limit(limit)
                .collect(Collectors.joining("、"));
    }

    private double aggregateConfidence(List<AgentResult> results) {
        List<Double> values = results.stream()
                .map(AgentResult::confidence)
                .filter(Objects::nonNull)
                .toList();
        if (values.isEmpty()) {
            return 0.72d;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.72d);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Agent 轨迹序列化失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Object readJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JsonProcessingException exception) {
            return value;
        }
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

    private Collection<?> asCollection(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        return List.of();
    }

    private List<String> stringItems(Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::stringValue)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value instanceof String text && Boolean.parseBoolean(text);
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private double numberValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private double boundedConfidence(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String nestedString(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            return stringValue(map.get(key));
        }
        if (value == null || !StringUtils.hasText(key)) {
            return "";
        }
        try {
            Method method = value.getClass().getMethod(key);
            return stringValue(method.invoke(value));
        } catch (ReflectiveOperationException exception) {
            return "";
        }
    }

    private double fieldNumber(Object value, String key, double fallback) {
        if (value instanceof Map<?, ?> map) {
            return numberValue(map.get(key), fallback);
        }
        if (value == null || !StringUtils.hasText(key)) {
            return fallback;
        }
        try {
            Method method = value.getClass().getMethod(key);
            return numberValue(method.invoke(value), fallback);
        } catch (ReflectiveOperationException exception) {
            return fallback;
        }
    }

    private Object fieldValue(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            return map.get(key);
        }
        if (value == null || !StringUtils.hasText(key)) {
            return null;
        }
        try {
            Method method = value.getClass().getMethod(key);
            return method.invoke(value);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private Double fieldDouble(Object value, String key) {
        Object fieldValue = fieldValue(value, key);
        if (fieldValue instanceof Number number) {
            return number.doubleValue();
        }
        if (fieldValue instanceof String text && StringUtils.hasText(text)) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalDateTime localDateTimeValue(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return LocalDateTime.parse(text.trim());
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private <T> List<T> emptyIfNull(List<T> values) {
        return values == null ? List.of() : values;
    }

    private boolean containsIgnoreCase(String text, String token) {
        return StringUtils.hasText(text)
                && StringUtils.hasText(token)
                && text.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }

    private boolean containsAny(String text, Collection<String> tokens) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return tokens.stream().anyMatch(token -> containsIgnoreCase(text, token));
    }

    private boolean containsToken(String text, String token) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(token)) {
            return false;
        }
        String normalizedText = " " + text.replaceAll("[^A-Za-z0-9]+", " ").toUpperCase(Locale.ROOT) + " ";
        return normalizedText.contains(" " + token.toUpperCase(Locale.ROOT) + " ");
    }

    private void addIfPresent(List<String> values, boolean present, String value) {
        if (present && StringUtils.hasText(value) && !values.contains(value)) {
            values.add(value);
        }
    }

    private List<String> distinctNonBlank(Collection<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String normalizeNameToken(String value) {
        return safe(value)
                .replace(" ", "")
                .replace("　", "")
                .replace("_", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String readableError(RuntimeException exception) {
        String message = exception.getMessage();
        return StringUtils.hasText(message) ? message : exception.getClass().getSimpleName();
    }

    private record AgentDefinition(
            String name,
            String role,
            Function<AgentContext, AgentResult> handler
    ) {
    }

    private record ObservationQaSnapshot(
            String source,
            Long id,
            String ecosystemName,
            LocalDateTime observedAt,
            BigDecimal locationLat,
            BigDecimal locationLng,
            String locationName,
            Object environment,
            Collection<?> speciesItems,
            String note
    ) {
        ObservationQaSnapshot {
            speciesItems = speciesItems == null ? List.of() : List.copyOf(speciesItems);
        }

        boolean hasObservationFacts() {
            return id != null
                    || StringUtils.hasText(ecosystemName)
                    || observedAt != null
                    || locationLat != null
                    || locationLng != null
                    || StringUtils.hasText(locationName)
                    || !speciesItems.isEmpty();
        }

        String sourceDescription() {
            List<String> parts = new ArrayList<>();
            if (id != null) {
                parts.add("id=" + id);
            }
            if (StringUtils.hasText(ecosystemName)) {
                parts.add("ecosystem=" + ecosystemName.trim());
            }
            if (observedAt != null) {
                parts.add("observedAt=" + observedAt);
            }
            if (locationLat != null && locationLng != null) {
                parts.add("coordinates=" + locationLat + "," + locationLng);
            }
            if (!speciesItems.isEmpty()) {
                parts.add("speciesItems=" + speciesItems.size());
            }
            return parts.isEmpty() ? source : String.join("; ", parts);
        }
    }

    private record SystemDataQuery(
            String speciesKeyword,
            String ecosystemKeyword,
            String locationKeyword,
            String protectionLevel,
            String iucnStatus
    ) {
        boolean hasSpeciesSignal() {
            return StringUtils.hasText(speciesKeyword)
                    || StringUtils.hasText(protectionLevel)
                    || StringUtils.hasText(iucnStatus)
                    || StringUtils.hasText(locationKeyword);
        }

        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            putIfPresent(values, "speciesKeyword", speciesKeyword);
            putIfPresent(values, "ecosystemKeyword", ecosystemKeyword);
            putIfPresent(values, "locationKeyword", locationKeyword);
            putIfPresent(values, "protectionLevel", protectionLevel);
            putIfPresent(values, "iucnStatus", iucnStatus);
            return values;
        }

        private void putIfPresent(Map<String, Object> values, String key, String value) {
            if (StringUtils.hasText(value)) {
                values.put(key, value);
            }
        }
    }

    private record TaxonomyName(
            String chineseName,
            String scientificName,
            double confidence
    ) {
        boolean hasAnyName() {
            return StringUtils.hasText(chineseName) || StringUtils.hasText(scientificName);
        }

        String key() {
            return StringUtils.hasText(scientificName)
                    ? scientificName.trim()
                    : chineseName.trim();
        }

        String displayName() {
            if (StringUtils.hasText(chineseName) && StringUtils.hasText(scientificName)) {
                return chineseName.trim() + " / " + scientificName.trim();
            }
            return StringUtils.hasText(chineseName) ? chineseName.trim() : scientificName.trim();
        }

        Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            if (StringUtils.hasText(chineseName)) {
                values.put("chineseName", chineseName.trim());
            }
            if (StringUtils.hasText(scientificName)) {
                values.put("scientificName", scientificName.trim());
            }
            if (confidence > 0) {
                values.put("confidence", confidence);
            }
            return values;
        }
    }
}
