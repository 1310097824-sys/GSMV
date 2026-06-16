package com.gsmv.ai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsmv.ai.AiModelGateway;
import com.gsmv.ai.agent.dto.AgentDtos;
import com.gsmv.ai.agent.mapper.AgentRunMapper;
import com.gsmv.ai.agent.model.AgentRun;
import com.gsmv.ai.agent.model.AgentStep;
import com.gsmv.ai.rag.RagKnowledgeService;
import com.gsmv.ai.rag.RagSearchHit;
import com.gsmv.ecosystem.mapper.EcosystemMapper;
import com.gsmv.ecosystem.model.Ecosystem;
import com.gsmv.observation.dto.ObservationView;
import com.gsmv.observation.dto.ObservationSpeciesView;
import com.gsmv.observation.mapper.ObservationMapper;
import com.gsmv.report.ReportService;
import com.gsmv.report.dto.DashboardSummary;
import com.gsmv.report.dto.EcosystemAnalyticsPoint;
import com.gsmv.report.dto.NameValuePoint;
import com.gsmv.species.dto.SpeciesRow;
import com.gsmv.species.mapper.SpeciesMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentOrchestratorServiceTests {

    private AgentRunMapper mapper;
    private RagKnowledgeService ragKnowledgeService;
    private ReportService reportService;
    private SpeciesMapper speciesMapper;
    private ObservationMapper observationMapper;
    private EcosystemMapper ecosystemMapper;
    private AiModelGateway aiModelGateway;
    private AgentOrchestratorService service;
    private AtomicReference<AgentRun> runRef;
    private List<AgentStep> steps;

    @BeforeEach
    void setUp() {
        mapper = mock(AgentRunMapper.class);
        ragKnowledgeService = mock(RagKnowledgeService.class);
        reportService = mock(ReportService.class);
        speciesMapper = mock(SpeciesMapper.class);
        observationMapper = mock(ObservationMapper.class);
        ecosystemMapper = mock(EcosystemMapper.class);
        aiModelGateway = mock(AiModelGateway.class);
        service = new AgentOrchestratorService(
                mapper,
                new ObjectMapper(),
                ragKnowledgeService,
                reportService,
                speciesMapper,
                observationMapper,
                ecosystemMapper,
                aiModelGateway
        );
        runRef = new AtomicReference<>();
        steps = new ArrayList<>();

        doAnswer(invocation -> {
            AgentRun run = invocation.getArgument(0);
            run.setId(1L);
            run.setCreatedAt(LocalDateTime.now());
            runRef.set(run);
            return null;
        }).when(mapper).insertRun(any(AgentRun.class));
        doAnswer(invocation -> {
            AgentStep step = invocation.getArgument(0);
            step.setId((long) steps.size() + 1);
            steps.add(step);
            return null;
        }).when(mapper).insertStep(any(AgentStep.class));
        doAnswer(invocation -> {
            AgentRun run = runRef.get();
            run.setStatus(invocation.getArgument(1));
            run.setSummary(invocation.getArgument(2));
            run.setVerificationStatus(invocation.getArgument(3));
            run.setConfidence(invocation.getArgument(4));
            run.setFinalOutputJson(invocation.getArgument(5));
            run.setFinishedAt(invocation.getArgument(6));
            return null;
        }).when(mapper).finishRun(eq(1L), any(), any(), any(), any(), any(), any());
        when(mapper.findRunById(1L)).thenAnswer(invocation -> runRef.get());
        when(mapper.findStepsByRunId(1L)).thenAnswer(invocation -> steps);
        when(reportService.dashboardSummary()).thenReturn(new DashboardSummary(12, 40, 4, 3, 5));
        when(speciesMapper.findPage(any(), any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(observationMapper.findPage(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(observationMapper.findSpeciesViews(any())).thenReturn(List.of());
        when(ecosystemMapper.findPage(any(), any(), anyInt(), anyInt())).thenReturn(List.of());
    }

    @Test
    void assistantWorkflowRecordsCoreAgentsAndVerifiedResult() {
        when(ragKnowledgeService.retrieveForScenario(any(), any(), anyInt())).thenReturn(List.of(
                new RagSearchHit(1L, 2L, "SPECIES", 3L, "中华白海豚资料", "保护状态摘要", "content", 0.9d, 0.8d, 0.1d, "/species/3")
        ));

        AgentDtos.AgentRunView run = service.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_ASSISTANT_CHAT,
                "ASSISTANT",
                null,
                "介绍中华白海豚",
                RagKnowledgeService.SCENARIO_ASSISTANT,
                Map.of("answer", "中华白海豚是近岸鲸豚。"),
                List.of()
        ));

        assertEquals("SUCCESS", run.status());
        assertEquals(AgentOrchestratorService.STATUS_VERIFIED, run.verificationStatus());
        assertEquals(4, run.steps().size());
        assertEquals("Coordinator Agent", run.steps().get(0).agentName());
        assertEquals("Verifier Agent", run.steps().get(3).agentName());
        assertNotNull(run.confidence());
        assertTrue(run.confidence() > 0.7d);

        AgentDtos.AgentRunView replay = service.getRunSnapshot(run.id());
        assertEquals(run.id(), replay.id());
        assertEquals(run.steps().size(), replay.steps().size());
        assertEquals(run.verificationStatus(), replay.verificationStatus());
        assertNotNull(replay.finalOutput());
        assertTrue(run.finalOutput() instanceof Map<?, ?>);
        Map<?, ?> finalOutput = (Map<?, ?>) run.finalOutput();
        assertEquals("中华白海豚是近岸鲸豚。", finalOutput.get("finalAnswer"));
        assertTrue(finalOutput.get("structuredContext") instanceof Map<?, ?>);
        assertTrue(finalOutput.get("evidenceSnapshot") instanceof List<?>);
        assertFalse(((List<?>) finalOutput.get("evidenceSnapshot")).isEmpty());
    }

    @Test
    void assistantWorkflowComposesFinalAnswerFromAgentEvidenceWithoutLegacyAnswer() {
        when(ragKnowledgeService.retrieveForScenario(any(), any(), anyInt())).thenReturn(List.of(
                new RagSearchHit(1L, 2L, "SPECIES", 3L, "Agent evidence", "protected species summary", "content", 0.9d, 0.8d, 0.1d, "/species/3")
        ));

        AgentDtos.AgentRunView run = service.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_ASSISTANT_CHAT,
                "ASSISTANT",
                null,
                "introduce dolphin",
                RagKnowledgeService.SCENARIO_ASSISTANT,
                Map.of("fallbackAnswer", "legacy fallback answer"),
                List.of()
        ));

        assertEquals(AgentOrchestratorService.STATUS_VERIFIED, run.verificationStatus());
        assertTrue(run.finalOutput() instanceof Map<?, ?>);
        Map<?, ?> finalOutput = (Map<?, ?>) run.finalOutput();
        String finalAnswer = String.valueOf(finalOutput.get("finalAnswer"));
        assertFalse(finalAnswer.contains("legacy fallback answer"));
        assertTrue(finalAnswer.contains("Agent 协作链路"));
        assertTrue(finalAnswer.contains("知识库召回"));
        assertTrue(finalAnswer.contains("Agent evidence"));
    }

    @Test
    void emptyRagEvidenceMarksKnowledgeGovernanceAsInsufficient() {
        when(ragKnowledgeService.retrieveForScenario(any(), any(), anyInt())).thenReturn(List.of());

        AgentDtos.AgentRunView run = service.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_KNOWLEDGE_GOVERNANCE,
                "RAG_DOCUMENT",
                8L,
                "检查重复知识",
                RagKnowledgeService.SCENARIO_ASSISTANT,
                Map.of(),
                List.of()
        ));

        assertEquals(AgentOrchestratorService.STATUS_INSUFFICIENT_EVIDENCE, run.verificationStatus());
        assertTrue(run.steps().stream().anyMatch(step -> "RAG Evidence Agent".equals(step.agentName())));
        assertTrue(run.confidence() <= 0.64d);
    }

    @Test
    void knowledgeGovernanceRunBuildsFindingsActionsAndTaskDraft() {
        when(ragKnowledgeService.retrieveForScenario(any(), any(), anyInt())).thenReturn(List.of());

        AgentDtos.AgentRunView run = service.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_KNOWLEDGE_GOVERNANCE,
                "RAG_GOVERNANCE_SWEEP",
                null,
                "scheduled governance sweep",
                RagKnowledgeService.SCENARIO_ASSISTANT,
                Map.of(
                        "trigger", "TEST",
                        "scannedDocumentCount", 4,
                        "failedDocuments", List.of(Map.of(
                                "issueType", "FAILED_DOCUMENT",
                                "documentId", 1L,
                                "title", "failed.pdf",
                                "status", "FAILED",
                                "errorMessage", "parse failed",
                                "reason", "index failed"
                        )),
                        "emptyChunkDocuments", List.of(Map.of(
                                "issueType", "EMPTY_CHUNKS",
                                "documentId", 2L,
                                "title", "empty.pdf",
                                "status", "READY",
                                "chunkCount", 0,
                                "reason", "no chunks"
                        )),
                        "duplicateGroups", List.of(Map.of(
                                "issueType", "DUPLICATE_TITLE",
                                "title", "Duplicate Knowledge",
                                "documentCount", 2,
                                "documents", List.of(
                                        Map.of("documentId", 3L),
                                        Map.of("documentId", 4L)
                                )
                        ))
                ),
                List.of()
        ));

        assertEquals(AgentOrchestratorService.STATUS_NEEDS_REVIEW, run.verificationStatus());
        AgentDtos.AgentStepView ragStep = run.steps().stream()
                .filter(step -> "RAG Evidence Agent".equals(step.agentName()))
                .findFirst()
                .orElseThrow();
        assertEquals("SUCCESS", ragStep.status());
        assertTrue(ragStep.output() instanceof Map<?, ?>);
        Map<?, ?> ragOutput = (Map<?, ?>) ragStep.output();
        assertEquals(3, ((Number) ragOutput.get("governanceIssueCount")).intValue());
        assertTrue(ragStep.evidence() instanceof List<?>);
        List<?> ragEvidence = (List<?>) ragStep.evidence();
        assertTrue(ragEvidence.stream().anyMatch(item -> "RAG_GOVERNANCE_FAILED_DOCUMENT".equals(((Map<?, ?>) item).get("type"))));

        assertTrue(run.finalOutput() instanceof Map<?, ?>);
        Map<?, ?> finalOutput = (Map<?, ?>) run.finalOutput();
        assertTrue(finalOutput.get("governanceFindings") instanceof List<?>);
        List<?> findings = (List<?>) finalOutput.get("governanceFindings");
        assertEquals(3, findings.size());
        assertTrue(finalOutput.get("actionItems") instanceof List<?>);
        List<?> actions = (List<?>) finalOutput.get("actionItems");
        assertEquals(3, actions.size());
        assertTrue(actions.stream().anyMatch(item -> "REINDEX_FAILED_DOCUMENTS".equals(((Map<?, ?>) item).get("code"))));
        assertTrue(finalOutput.get("governanceTaskDraft") instanceof Map<?, ?>);
        Map<?, ?> taskDraft = (Map<?, ?>) finalOutput.get("governanceTaskDraft");
        assertEquals("KNOWLEDGE_GOVERNANCE_REVIEW", taskDraft.get("type"));
        assertEquals(Boolean.TRUE, taskDraft.get("humanReviewRequired"));

        AgentDtos.AgentStepView verifierStep = run.steps().stream()
                .filter(step -> "Verifier Agent".equals(step.agentName()))
                .findFirst()
                .orElseThrow();
        assertTrue(verifierStep.output() instanceof Map<?, ?>);
        Map<?, ?> verifierOutput = (Map<?, ?>) verifierStep.output();
        assertEquals(0, ((Number) verifierOutput.get("unsupportedClaimCount")).intValue());
    }

    @Test
    void verifierFlagsUnsupportedClaimsWhenNoEvidenceSupportsSummary() {
        when(ragKnowledgeService.retrieveForScenario(any(), any(), anyInt())).thenReturn(List.of());

        AgentDtos.AgentRunView run = service.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_KNOWLEDGE_GOVERNANCE,
                "RAG_DOCUMENT",
                8L,
                "governance claim check",
                RagKnowledgeService.SCENARIO_ASSISTANT,
                Map.of("summary", "This document contains a confirmed protected-species conflict."),
                List.of()
        ));

        AgentDtos.AgentStepView verifierStep = run.steps().stream()
                .filter(step -> "Verifier Agent".equals(step.agentName()))
                .findFirst()
                .orElseThrow();
        assertTrue(verifierStep.output() instanceof Map<?, ?>);
        Map<?, ?> output = (Map<?, ?>) verifierStep.output();
        assertTrue(output.get("claimChecks") instanceof List<?>);
        List<?> claimChecks = (List<?>) output.get("claimChecks");
        assertTrue(claimChecks.stream().anyMatch(check -> "UNSUPPORTED".equals(((Map<?, ?>) check).get("supportLevel"))));
        assertEquals(1, ((Number) output.get("unsupportedClaimCount")).intValue());
        assertTrue(output.get("reviewFindings") instanceof List<?>);
        List<?> findings = (List<?>) output.get("reviewFindings");
        assertTrue(findings.stream().anyMatch(finding -> "UNSUPPORTED_CLAIMS".equals(((Map<?, ?>) finding).get("code"))));
        assertEquals(AgentOrchestratorService.STATUS_INSUFFICIENT_EVIDENCE, run.verificationStatus());
    }

    @Test
    void replaySnapshotRestoresStoredEvidenceAndFinalConclusion() {
        when(ragKnowledgeService.retrieveForScenario(any(), any(), anyInt())).thenReturn(List.of());

        AgentDtos.AgentRunView run = service.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_KNOWLEDGE_GOVERNANCE,
                "RAG_DOCUMENT",
                8L,
                "governance replay check",
                RagKnowledgeService.SCENARIO_ASSISTANT,
                Map.of("summary", "This document contains a confirmed protected-species conflict."),
                List.of()
        ));

        AgentDtos.AgentRunReplayView replay = service.getRunReplaySnapshot(run.id());

        assertNotNull(replay);
        assertEquals(run.id(), replay.runId());
        assertEquals("READY", replay.replayStatus());
        assertTrue(replay.reconstructable());
        assertEquals(run.steps().size(), replay.stepCount());
        assertTrue(replay.evidenceCount() > 0);
        assertEquals(run.steps().stream().map(AgentDtos.AgentStepView::agentName).toList(), replay.agentSequence());
        assertTrue(replay.reconstructedFinalOutput() instanceof Map<?, ?>);
        Map<?, ?> reconstructed = (Map<?, ?>) replay.reconstructedFinalOutput();
        assertEquals(run.verificationStatus(), reconstructed.get("verificationStatus"));
        assertTrue(replay.verifierOutput() instanceof Map<?, ?>);
        assertTrue(replay.claimChecks() instanceof List<?>);
        assertFalse(((List<?>) replay.claimChecks()).isEmpty());
        assertTrue(replay.reviewFindings() instanceof List<?>);
        assertTrue(replay.consistencyIssues().isEmpty());
    }

    @Test
    void lowConfidenceVisionResultRequiresHumanReview() {
        when(ragKnowledgeService.retrieveForScenario(any(), any(), anyInt())).thenReturn(List.of());

        AgentDtos.AgentRunView run = service.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_SPECIES_IDENTIFY,
                "IMAGE",
                null,
                "模糊海洋生物图片",
                RagKnowledgeService.SCENARIO_IMAGE_IDENTIFICATION,
                Map.of(
                        "confidence", 0.42d,
                        "needsHumanReview", true,
                        "candidates", List.of(Map.of("scientificName", "candidate", "confidence", 0.42d))
                ),
                List.of()
        ));

        assertEquals(AgentOrchestratorService.STATUS_NEEDS_REVIEW, run.verificationStatus());
        AgentDtos.AgentStepView visionStep = run.steps().stream()
                .filter(step -> "Vision Review Agent".equals(step.agentName()))
                .findFirst()
                .orElseThrow();
        assertTrue(visionStep.output() instanceof Map<?, ?>);
        Map<?, ?> visionOutput = (Map<?, ?>) visionStep.output();
        assertEquals(Boolean.TRUE, visionOutput.get("shouldCreateHumanReviewTicket"));
        assertTrue(visionOutput.get("reviewReasons") instanceof List<?>);
        assertTrue(((List<?>) visionOutput.get("reviewReasons")).stream().anyMatch(item -> String.valueOf(item).contains("置信度")));
        assertTrue(visionOutput.get("reviewTicketDraft") instanceof Map<?, ?>);
        Map<?, ?> reviewTicketDraft = (Map<?, ?>) visionOutput.get("reviewTicketDraft");
        assertEquals(Boolean.TRUE, reviewTicketDraft.get("needsHumanReview"));
        assertTrue(run.finalOutput() instanceof Map<?, ?>);
        Map<?, ?> finalOutput = (Map<?, ?>) run.finalOutput();
        assertTrue(finalOutput.get("reviewTicketDraft") instanceof Map<?, ?>);
        assertFalse(run.steps().isEmpty());
        BigDecimal persistedConfidence = runRef.get().getConfidence();
        assertNotNull(persistedConfidence);
        assertTrue(persistedConfidence.doubleValue() <= 0.72d);
    }

    @Test
    void speciesWorkflowUsesPlannedReviewOrderAndKeepsRagEvidenceInFinalOutput() {
        when(ragKnowledgeService.retrieveForScenario(any(), any(), anyInt())).thenReturn(List.of(
                new RagSearchHit(11L, 22L, "SPECIES", 33L, "Sousa chinensis profile", "Chinese white dolphin facts", "Sousa chinensis is a coastal dolphin.", 0.91d, 0.8d, 0.11d, "/species/33")
        ));

        AgentDtos.AgentRunView run = service.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_SPECIES_IDENTIFY,
                "IMAGE",
                null,
                "Chinese white dolphin Sousa chinensis",
                RagKnowledgeService.SCENARIO_IMAGE_IDENTIFICATION,
                Map.of(
                        "likelyChineseName", "Chinese white dolphin",
                        "likelyScientificName", "Sousa chinensis",
                        "confidence", 0.88d,
                        "needsHumanReview", false,
                        "ragEvidencePending", true,
                        "ragEvidence", List.of(),
                        "conflictWarnings", List.of(),
                        "candidates", List.of(Map.of(
                                "chineseName", "Chinese white dolphin",
                                "scientificName", "Sousa chinensis",
                                "confidence", 0.88d
                        ))
                ),
                List.of()
        ));

        assertEquals(
                List.of("Coordinator Agent", "Vision Review Agent", "Taxonomy Agent", "RAG Evidence Agent", "Verifier Agent"),
                run.steps().stream().map(AgentDtos.AgentStepView::agentName).toList()
        );
        AgentDtos.AgentStepView ragStep = run.steps().stream()
                .filter(step -> "RAG Evidence Agent".equals(step.agentName()))
                .findFirst()
                .orElseThrow();
        assertTrue(ragStep.evidence() instanceof List<?>);
        assertEquals(1, ((List<?>) ragStep.evidence()).size());
        assertTrue(run.finalOutput() instanceof Map<?, ?>);
        Map<?, ?> finalOutput = (Map<?, ?>) run.finalOutput();
        assertTrue(finalOutput.get("evidenceSnapshot") instanceof List<?>);
        assertFalse(((List<?>) finalOutput.get("evidenceSnapshot")).isEmpty());
    }

    @Test
    void visionReviewAgentUsesTransientImageBytesWithoutPersistingThem() throws Exception {
        when(aiModelGateway.bailianVisionJson(anyString(), anyString(), any(byte[].class), eq("image/png")))
                .thenReturn(new ObjectMapper().readTree("""
                        {
                          "likelyChineseName": "Chinese white dolphin",
                          "likelyScientificName": "Sousa chinensis",
                          "confidence": 0.86,
                          "reasoning": "clear dorsal shape",
                          "candidates": [
                            {"chineseName": "Chinese white dolphin", "scientificName": "Sousa chinensis", "confidence": 0.86, "reason": "body shape"}
                          ]
                        }
                        """));
        when(ragKnowledgeService.retrieveForScenario(any(), any(), anyInt())).thenReturn(List.of());

        AgentDtos.AgentRunView run = service.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_SPECIES_IDENTIFY,
                "IMAGE",
                null,
                "uploaded-image.png",
                RagKnowledgeService.SCENARIO_IMAGE_IDENTIFICATION,
                Map.of(
                        "contentType", "image/png",
                        "lowConfidenceThreshold", 0.7d,
                        "ragEvidencePending", true,
                        "ragEvidence", List.of(),
                        "conflictWarnings", List.of()
                ),
                List.of(),
                Map.of(
                        "imageBytes", new byte[] {1, 2, 3},
                        "contentType", "image/png"
                )
        ));

        verify(aiModelGateway).bailianVisionJson(anyString(), anyString(), any(byte[].class), eq("image/png"));
        AgentDtos.AgentStepView visionStep = run.steps().stream()
                .filter(step -> "Vision Review Agent".equals(step.agentName()))
                .findFirst()
                .orElseThrow();
        assertTrue(visionStep.output() instanceof Map<?, ?>);
        Map<?, ?> visionOutput = (Map<?, ?>) visionStep.output();
        assertEquals("Chinese white dolphin", visionOutput.get("likelyChineseName"));
        assertEquals("Sousa chinensis", visionOutput.get("likelyScientificName"));
        assertFalse(steps.stream()
                .filter(step -> "Vision Review Agent".equals(step.getAgentName()))
                .findFirst()
                .orElseThrow()
                .getInputJson()
                .contains("imageBytes"));
    }

    @Test
    void speciesProfileAssistWorkflowRunsTaxonomyCompletionWithRagEvidence() throws Exception {
        when(ragKnowledgeService.retrieveForScenario(any(), any(), anyInt())).thenReturn(List.of(
                new RagSearchHit(11L, 22L, "SPECIES", 33L, "Sousa chinensis profile", "Chinese white dolphin facts", "Sousa chinensis is a coastal dolphin.", 0.91d, 0.8d, 0.11d, "/species/33")
        ));
        when(aiModelGateway.deepSeekJson(any())).thenReturn(new ObjectMapper().readTree("""
                {
                  "chineseName": "Chinese white dolphin",
                  "scientificName": "Sousa chinensis",
                  "phylumName": "Chordata",
                  "className": "Mammalia",
                  "orderName": "Cetacea",
                  "familyName": "Delphinidae",
                  "genusName": "Sousa",
                  "summary": "Agent generated profile",
                  "confidence": 0.84,
                  "notes": ["supported by retrieved evidence"]
                }
                """));

        AgentDtos.AgentRunView run = service.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_SPECIES_PROFILE_ASSIST,
                "SPECIES_PROFILE",
                null,
                "Chinese white dolphin",
                RagKnowledgeService.SCENARIO_SPECIES_PROFILE,
                Map.of(
                        "chineseName", "Chinese white dolphin",
                        "scientificName", "Sousa chinensis"
                ),
                List.of()
        ));

        verify(aiModelGateway).deepSeekJson(any());
        assertEquals(
                List.of("Coordinator Agent", "RAG Evidence Agent", "Taxonomy Agent", "Verifier Agent"),
                run.steps().stream().map(AgentDtos.AgentStepView::agentName).toList()
        );
        AgentDtos.AgentStepView taxonomyStep = run.steps().stream()
                .filter(step -> "Taxonomy Agent".equals(step.agentName()))
                .findFirst()
                .orElseThrow();
        assertTrue(taxonomyStep.output() instanceof Map<?, ?>);
        Map<?, ?> taxonomyOutput = (Map<?, ?>) taxonomyStep.output();
        assertEquals("Chinese white dolphin", taxonomyOutput.get("chineseName"));
        assertEquals(1, taxonomyOutput.get("ragEvidenceCount"));
    }

    @Test
    void taxonomyAgentFlagsSystemAndExternalSourceConflicts() {
        SpeciesRow systemSpecies = new SpeciesRow(
                3L,
                2L,
                "SPECIES",
                "Sousa chinensis",
                "中华白海豚",
                "国家二级",
                "EN",
                "系统档案",
                null,
                null,
                "近岸海域",
                "中国东南沿海",
                null,
                null,
                "湛江近海",
                null,
                null,
                1,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(speciesMapper.findPage(any(), any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of(systemSpecies));
        when(ragKnowledgeService.retrieveForScenario(any(), any(), anyInt())).thenReturn(List.of());

        AgentDtos.AgentRunView run = service.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_SPECIES_IDENTIFY,
                "IMAGE",
                null,
                "中华白海豚识图复核",
                RagKnowledgeService.SCENARIO_IMAGE_IDENTIFICATION,
                Map.of(
                        "likelyChineseName", "中华白海豚",
                        "likelyScientificName", "Sousa chinensis",
                        "confidence", 0.86d,
                        "needsHumanReview", false,
                        "candidates", List.of(Map.of(
                                "chineseName", "中华白海豚",
                                "scientificName", "Sousa chinensis",
                                "confidence", 0.86d
                        )),
                        "relatedSpeciesRecords", List.of(Map.of(
                                "id", 3L,
                                "chineseName", "中华白海豚",
                                "scientificName", "Sousa chinensis",
                                "protectionLevel", "国家一级",
                                "iucnStatus", "VU"
                        )),
                        "ragEvidence", List.of(Map.of(
                                "sourceType", "EXTERNAL_IUCN",
                                "title", "IUCN external assessment",
                                "summary", "Sousa chinensis is listed as CR and 国家二级保护 in this source."
                        )),
                        "conflictWarnings", List.of()
                ),
                List.of()
        ));

        AgentDtos.AgentStepView taxonomyStep = run.steps().stream()
                .filter(step -> "Taxonomy Agent".equals(step.agentName()))
                .findFirst()
                .orElseThrow();
        assertTrue(taxonomyStep.output() instanceof Map<?, ?>);
        Map<?, ?> output = (Map<?, ?>) taxonomyStep.output();
        assertTrue(output.get("issues") instanceof List<?>);
        List<?> issues = (List<?>) output.get("issues");
        assertTrue(issues.stream().anyMatch(issue -> String.valueOf(((Map<?, ?>) issue).get("title")).contains("保护等级冲突")));
        assertTrue(issues.stream().anyMatch(issue -> String.valueOf(((Map<?, ?>) issue).get("title")).contains("IUCN")));
        assertTrue(taxonomyStep.summary().contains("冲突"));
        assertEquals(AgentOrchestratorService.STATUS_NEEDS_REVIEW, run.verificationStatus());
    }

    @Test
    void observationQaWorkflowRecordsQualityAgentsAndNeedsReview() {
        when(ragKnowledgeService.retrieveForScenario(any(), any(), anyInt())).thenReturn(List.of());

        AgentDtos.AgentRunView run = service.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_OBSERVATION_QA,
                "OBSERVATION",
                77L,
                "观测记录质量检查",
                RagKnowledgeService.SCENARIO_OBSERVATION_ANALYSIS,
                Map.of(
                        "score", 62,
                        "grade", "LOW",
                        "needsReview", true,
                        "issues", List.of(Map.of("severity", "HIGH", "title", "未关联物种"))
                ),
                List.of()
        ));

        assertEquals(AgentOrchestratorService.STATUS_NEEDS_REVIEW, run.verificationStatus());
        assertTrue(run.steps().stream().anyMatch(step -> "Observation QA Agent".equals(step.agentName())));
        assertTrue(run.steps().stream().anyMatch(step -> "Taxonomy Agent".equals(step.agentName())));
        assertEquals("OBSERVATION", run.subjectType());
        assertEquals(77L, run.subjectId());
    }

    @Test
    void observationQaAgentChecksStoredObservationRecord() {
        ObservationView observation = new ObservationView(
                77L,
                4L,
                "Coastal ecosystem",
                9L,
                "Researcher",
                LocalDateTime.now().plusDays(1),
                BigDecimal.valueOf(95.0d),
                BigDecimal.valueOf(190.0d),
                "",
                "{\"ph\":10.2,\"dissolvedOxygen\":2.1,\"depthMeters\":-1}",
                "Field note",
                LocalDateTime.now()
        );
        when(observationMapper.findViewById(77L)).thenReturn(observation);
        when(observationMapper.findSpeciesViews(77L)).thenReturn(List.of(new ObservationSpeciesView(
                3L,
                "Sousa chinensis",
                "Chinese white dolphin",
                1,
                -5,
                "feeding",
                ""
        )));
        when(ragKnowledgeService.retrieveForScenario(any(), any(), anyInt())).thenReturn(List.of());

        AgentDtos.AgentRunView run = service.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_OBSERVATION_QA,
                "OBSERVATION",
                77L,
                "Stored observation quality check",
                RagKnowledgeService.SCENARIO_OBSERVATION_ANALYSIS,
                Map.of(),
                List.of()
        ));

        AgentDtos.AgentStepView qaStep = run.steps().stream()
                .filter(step -> "Observation QA Agent".equals(step.agentName()))
                .findFirst()
                .orElseThrow();
        assertTrue(qaStep.output() instanceof Map<?, ?>);
        Map<?, ?> output = (Map<?, ?>) qaStep.output();
        assertTrue(output.get("qaIssues") instanceof List<?>);
        List<?> qaIssues = (List<?>) output.get("qaIssues");
        List<String> codes = qaIssues.stream()
                .map(issue -> String.valueOf(((Map<?, ?>) issue).get("code")))
                .toList();
        assertTrue(codes.contains("OBSERVED_AT_FUTURE"));
        assertTrue(codes.contains("LATITUDE_OUT_OF_RANGE"));
        assertTrue(codes.contains("LONGITUDE_OUT_OF_RANGE"));
        assertTrue(codes.contains("SPECIES_COUNT_NEGATIVE"));
        assertTrue(codes.contains("PH_OUT_OF_RANGE"));
        assertTrue(codes.contains("DISSOLVED_OXYGEN_OUT_OF_RANGE"));
        assertTrue(codes.contains("DEPTH_NEGATIVE"));
        assertTrue(output.get("actionItems") instanceof List<?>);
        List<?> actionItems = (List<?>) output.get("actionItems");
        assertTrue(actionItems.stream().anyMatch(action -> String.valueOf(((Map<?, ?>) action).get("code")).contains("LATITUDE_OUT_OF_RANGE")));
        assertTrue(output.get("reviewTaskDraft") instanceof Map<?, ?>);
        Map<?, ?> reviewTaskDraft = (Map<?, ?>) output.get("reviewTaskDraft");
        assertEquals("OBSERVATION_QA_REVIEW", reviewTaskDraft.get("taskType"));
        assertEquals("HIGH", reviewTaskDraft.get("priority"));
        assertTrue(qaStep.evidence() instanceof List<?>);
        assertTrue(((List<?>) qaStep.evidence()).stream()
                .anyMatch(item -> "OBSERVATION_QA_RULE".equals(((Map<?, ?>) item).get("type"))));
        assertTrue(run.finalOutput() instanceof Map<?, ?>);
        Map<?, ?> finalOutput = (Map<?, ?>) run.finalOutput();
        assertTrue(finalOutput.get("riskFindings") instanceof List<?>);
        assertFalse(((List<?>) finalOutput.get("riskFindings")).isEmpty());
        assertTrue(finalOutput.get("actionItems") instanceof List<?>);
        assertFalse(((List<?>) finalOutput.get("actionItems")).isEmpty());
        assertTrue(finalOutput.get("reviewTaskDraft") instanceof Map<?, ?>);
        assertEquals(AgentOrchestratorService.STATUS_NEEDS_REVIEW, run.verificationStatus());
    }

    @Test
    void researchReportWorkflowRecordsReportAnalystAndEvidence() {
        when(ragKnowledgeService.retrieveForScenario(any(), any(), anyInt())).thenReturn(List.of(
                new RagSearchHit(9L, 10L, "AI_REPORT", 11L, "历史报告", "趋势证据", "content", 0.82d, 0.7d, 0.12d, "/ai-reports")
        ));

        AgentDtos.AgentRunView run = service.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_RESEARCH_REPORT,
                "AI_RESEARCH_REPORT",
                18L,
                "月度科研简报",
                RagKnowledgeService.SCENARIO_REPORT,
                Map.of(
                        "title", "GSMV 月度科研简报",
                        "summary", "近 30 天观测稳定。",
                        "highlights", List.of("观测覆盖 4 个生态系统"),
                        "risks", List.of("部分物种保护等级未补齐"),
                        "recommendations", List.of("复核重点观测"),
                        "evidence", List.of("系统统计")
                ),
                List.of()
        ));

        assertEquals("SUCCESS", run.status());
        assertEquals(AgentOrchestratorService.STATUS_VERIFIED, run.verificationStatus());
        assertTrue(run.steps().stream().anyMatch(step -> "Report Analyst Agent".equals(step.agentName())));
        assertTrue(run.steps().stream().anyMatch(step -> "RAG Evidence Agent".equals(step.agentName())));
        assertEquals("AI_RESEARCH_REPORT", run.subjectType());
    }

    @Test
    void reportAnalystProducesStructuredSignalsAndEvidenceMap() {
        when(reportService.observationTrend(30)).thenReturn(List.of(
                new NameValuePoint("2026-06-01", 1),
                new NameValuePoint("2026-06-02", 2),
                new NameValuePoint("2026-06-03", 10)
        ));
        when(reportService.observationActivityByUser(30)).thenReturn(List.of(new NameValuePoint("Alice", 13)));
        when(reportService.ecosystemAnalytics()).thenReturn(List.of(new EcosystemAnalyticsPoint(
                4L,
                "Reef survey area",
                "MARINE",
                13,
                5
        )));
        when(reportService.protectionLevelDistribution()).thenReturn(List.of(
                new NameValuePoint("Unknown", 2),
                new NameValuePoint("National I", 4)
        ));
        when(ragKnowledgeService.retrieveForScenario(any(), any(), anyInt())).thenReturn(List.of(
                new RagSearchHit(9L, 10L, "AI_REPORT", 11L, "Trend evidence", "survey evidence", "content", 0.85d, 0.72d, 0.05d, "/ai-reports")
        ));

        AgentDtos.AgentRunView run = service.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_RESEARCH_REPORT,
                "AI_RESEARCH_REPORT",
                18L,
                "Monthly biodiversity report",
                RagKnowledgeService.SCENARIO_REPORT,
                Map.of(
                        "reportType", "MONTHLY",
                        "days", 30,
                        "title", "Monthly biodiversity report",
                        "summary", "Observation activity increased in the latest period.",
                        "highlights", List.of("Observation coverage increased across the reef survey area."),
                        "risks", List.of("Protection level metadata is incomplete."),
                        "recommendations", List.of("Review protected species metadata."),
                        "evidence", List.of("Dashboard summary", "Trend evidence")
                ),
                List.of()
        ));

        AgentDtos.AgentStepView reportStep = run.steps().stream()
                .filter(step -> "Report Analyst Agent".equals(step.agentName()))
                .findFirst()
                .orElseThrow();
        assertTrue(reportStep.output() instanceof Map<?, ?>);
        Map<?, ?> output = (Map<?, ?>) reportStep.output();
        assertTrue(output.get("trendSignals") instanceof List<?>);
        assertTrue(output.get("riskSignals") instanceof List<?>);
        assertTrue(output.get("actionItems") instanceof List<?>);
        assertTrue(output.get("evidenceMap") instanceof List<?>);
        List<?> trendSignals = (List<?>) output.get("trendSignals");
        List<?> riskSignals = (List<?>) output.get("riskSignals");
        List<?> actionItems = (List<?>) output.get("actionItems");
        List<?> evidenceMap = (List<?>) output.get("evidenceMap");
        assertTrue(trendSignals.stream().anyMatch(signal -> "OBSERVATION_SPIKE".equals(((Map<?, ?>) signal).get("code"))));
        assertTrue(riskSignals.stream().anyMatch(signal -> "PROTECTION_GAP".equals(((Map<?, ?>) signal).get("code"))));
        assertTrue(actionItems.stream().anyMatch(action -> "ACTION_FILL_PROTECTION_LEVELS".equals(((Map<?, ?>) action).get("code"))));
        assertTrue(evidenceMap.stream().anyMatch(mapping -> "DIRECT".equals(((Map<?, ?>) mapping).get("supportLevel"))));
        assertTrue(reportStep.evidence() instanceof List<?>);
        assertTrue(((List<?>) reportStep.evidence()).stream()
                .anyMatch(item -> "REPORT_EVIDENCE_MAP".equals(((Map<?, ?>) item).get("type"))));
        assertTrue(run.finalOutput() instanceof Map<?, ?>);
        Map<?, ?> finalOutput = (Map<?, ?>) run.finalOutput();
        assertTrue(finalOutput.get("finalDraft") instanceof Map<?, ?>);
        assertTrue(finalOutput.get("actionItems") instanceof List<?>);
        assertTrue(finalOutput.get("structuredContext") instanceof Map<?, ?>);
        Map<?, ?> finalDraft = (Map<?, ?>) finalOutput.get("finalDraft");
        assertTrue(finalDraft.get("highlights") instanceof List<?>);
        assertTrue(finalDraft.get("risks") instanceof List<?>);
        assertTrue(finalDraft.get("recommendations") instanceof List<?>);
        assertTrue(finalDraft.get("evidence") instanceof List<?>);
        assertTrue(((List<?>) finalDraft.get("highlights")).stream().anyMatch(item -> String.valueOf(item).contains("Agent 趋势发现")));
        assertTrue(((List<?>) finalDraft.get("risks")).stream().anyMatch(item -> String.valueOf(item).contains("Agent 风险提示")));
        assertTrue(((List<?>) finalDraft.get("recommendations")).stream().anyMatch(item -> String.valueOf(item).contains("Review species files")));
        assertTrue(((List<?>) finalDraft.get("evidence")).stream().anyMatch(item -> String.valueOf(item).contains("证据映射")));
        assertEquals("SUCCESS", reportStep.status());
        assertEquals(AgentOrchestratorService.STATUS_VERIFIED, run.verificationStatus());
    }

    @Test
    void systemDataAgentAddsStructuredFactsForAssistantQueries() {
        SpeciesRow species = new SpeciesRow(
                3L,
                2L,
                "SPECIES",
                "Sousa chinensis",
                "中华白海豚",
                "国家一级",
                "VU",
                "近岸鲸豚",
                null,
                null,
                "河口和近岸海域",
                "中国东南沿海",
                null,
                null,
                "湛江近海",
                null,
                null,
                1,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        ObservationView observation = new ObservationView(
                7L,
                4L,
                "湛江近岸生态系统",
                1L,
                "研究员",
                LocalDateTime.now(),
                BigDecimal.valueOf(21.2d),
                BigDecimal.valueOf(110.4d),
                "湛江湾",
                "{}",
                "发现中华白海豚活动",
                LocalDateTime.now()
        );
        Ecosystem ecosystem = new Ecosystem();
        ecosystem.setId(4L);
        ecosystem.setName("湛江近岸生态系统");
        ecosystem.setType("近岸海域");
        ecosystem.setDescription("近岸观测重点区域");
        when(speciesMapper.findPage(any(), any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of(species));
        when(observationMapper.findPage(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of(observation));
        when(ecosystemMapper.findPage(any(), any(), anyInt(), anyInt())).thenReturn(List.of(ecosystem));
        when(ragKnowledgeService.retrieveForScenario(any(), any(), anyInt())).thenReturn(List.of());

        AgentDtos.AgentRunView run = service.execute(new AgentTask(
                AgentOrchestratorService.WORKFLOW_ASSISTANT_CHAT,
                "ASSISTANT",
                null,
                "湛江中华白海豚有哪些系统记录？",
                RagKnowledgeService.SCENARIO_ASSISTANT,
                Map.of("structuredQuery", Map.of(
                        "speciesKeyword", "中华白海豚",
                        "ecosystemKeyword", "湛江",
                        "locationKeyword", "湛江",
                        "protectionLevel", "国家一级",
                        "iucnStatus", "VU"
                )),
                List.of()
        ));

        AgentDtos.AgentStepView systemStep = run.steps().stream()
                .filter(step -> "System Data Agent".equals(step.agentName()))
                .findFirst()
                .orElseThrow();
        assertTrue(systemStep.summary().contains("站内结构化事实"));
        assertTrue(systemStep.evidence() instanceof List<?>);
        List<?> evidence = (List<?>) systemStep.evidence();
        assertTrue(evidence.stream().anyMatch(item -> "SYSTEM_SPECIES".equals(((Map<?, ?>) item).get("type"))));
        assertTrue(evidence.stream().anyMatch(item -> "SYSTEM_OBSERVATION".equals(((Map<?, ?>) item).get("type"))));
        assertTrue(evidence.stream().anyMatch(item -> "SYSTEM_ECOSYSTEM".equals(((Map<?, ?>) item).get("type"))));
        assertTrue(systemStep.output() instanceof Map<?, ?>);
        Map<?, ?> output = (Map<?, ?>) systemStep.output();
        assertEquals(1, output.get("speciesMatchCount"));
        assertEquals(1, output.get("observationMatchCount"));
        assertEquals(1, output.get("ecosystemMatchCount"));
    }
}
