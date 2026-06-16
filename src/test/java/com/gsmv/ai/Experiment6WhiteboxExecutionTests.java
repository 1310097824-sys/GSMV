package com.gsmv.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsmv.ai.agent.AgentOrchestratorService;
import com.gsmv.ai.agent.AgentTask;
import com.gsmv.ai.agent.dto.AgentDtos;
import com.gsmv.ai.dto.AssistantAiDtos;
import com.gsmv.ai.dto.ObservationAiDtos;
import com.gsmv.ai.dto.SpeciesAiDtos;
import com.gsmv.ai.rag.RagKnowledgeService;
import com.gsmv.ai.rag.RagTextChunker;
import com.gsmv.ai.rag.RagVectorUtils;
import com.gsmv.ai.report.AiReportService;
import com.gsmv.audit.service.AuditService;
import com.gsmv.common.PageResponse;
import com.gsmv.common.exception.BusinessException;
import com.gsmv.report.ReportService;
import com.gsmv.report.dto.DashboardSummary;
import com.gsmv.report.dto.EcosystemAnalyticsPoint;
import com.gsmv.report.dto.NameValuePoint;
import com.gsmv.species.SpeciesService;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

class Experiment6WhiteboxExecutionTests {

    private final RagTextChunker chunker = new RagTextChunker();

    @Test
    void utC01NullTextReturnsEmpty() {
        assertTrue(chunker.chunk("t", null).isEmpty());
    }

    @Test
    void utC02BlankTextReturnsEmpty() {
        assertTrue(chunker.chunk("t", " \t \n ").isEmpty());
    }

    @Test
    void utC03ShortTextCreatesSingleChunk() {
        List<RagTextChunker.ChunkDraft> chunks = chunker.chunk("title", "abc def");
        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).index());
        assertEquals("title", chunks.get(0).title());
    }

    @Test
    void utC04TextNormalizationRemovesExtraWhitespace() {
        List<RagTextChunker.ChunkDraft> chunks = chunker.chunk("t", " a\t\tb\r\n\r\n\r\nc ");
        assertEquals("a b\n\nc", chunks.get(0).content());
    }

    @Test
    void utC05LongTextCreatesMultipleChunks() {
        List<RagTextChunker.ChunkDraft> chunks = chunker.chunk("long", "x".repeat(1300));
        assertTrue(chunks.size() >= 3);
    }

    @Test
    void utC06LongChunksKeepOverlap() {
        List<RagTextChunker.ChunkDraft> chunks = chunker.chunk("long", "0123456789".repeat(130));
        assertTrue(chunks.size() >= 2);
        String firstTail = chunks.get(0).content().substring(chunks.get(0).content().length() - 80);
        assertTrue(chunks.get(1).content().contains(firstTail.substring(0, 20)));
    }

    @Test
    void utC07SummaryIsTruncatedForLongContent() {
        List<RagTextChunker.ChunkDraft> chunks = chunker.chunk("long", "abcdef ".repeat(100));
        assertTrue(chunks.get(0).summary().length() <= 183);
        assertTrue(chunks.get(0).summary().endsWith("..."));
    }

    @Test
    void utV01CosineNullVectorReturnsZero() {
        assertEquals(0.0d, RagVectorUtils.cosine(null, List.of(1.0d)));
    }

    @Test
    void utV02CosineEmptyVectorReturnsZero() {
        assertEquals(0.0d, RagVectorUtils.cosine(List.of(), List.of(1.0d)));
    }

    @Test
    void utV03CosineIdenticalVectorReturnsOne() {
        assertEquals(1.0d, RagVectorUtils.cosine(List.of(1.0d, 2.0d), List.of(1.0d, 2.0d)), 0.000001d);
    }

    @Test
    void utV04CosineOrthogonalVectorReturnsZero() {
        assertEquals(0.0d, RagVectorUtils.cosine(List.of(1.0d, 0.0d), List.of(0.0d, 1.0d)), 0.000001d);
    }

    @Test
    void utV05CosineTreatsNullElementAsZero() {
        assertEquals(1.0d, RagVectorUtils.cosine(Arrays.asList(null, 2.0d), List.of(0.0d, 2.0d)), 0.000001d);
    }

    @Test
    void utV06CosineClampsNegativeResultToZero() {
        assertEquals(0.0d, RagVectorUtils.cosine(List.of(-1.0d, 0.0d), List.of(1.0d, 0.0d)), 0.000001d);
    }

    @Test
    void utK01KeywordBlankQueryReturnsZero() {
        assertEquals(0.0d, RagVectorUtils.keywordScore("", "dolphin"));
    }

    @Test
    void utK02KeywordBlankTextReturnsZero() {
        assertEquals(0.0d, RagVectorUtils.keywordScore("dolphin", ""));
    }

    @Test
    void utK03KeywordFullPhraseReturnsOne() {
        assertEquals(1.0d, RagVectorUtils.keywordScore("Chinese white dolphin", "Chinese white dolphin appears here"));
    }

    @Test
    void utK04KeywordPartialTermsScoreFraction() {
        assertEquals(0.5d, RagVectorUtils.keywordScore("dolphin shark", "dolphin only"), 0.000001d);
    }

    @Test
    void utK05KeywordPunctuationSplitMatchesTerms() {
        assertEquals(1.0d, RagVectorUtils.keywordScore("dolphin, shark", "shark and dolphin"));
    }

    @Test
    void utK06KeywordShortChineseFallbackMatches() {
        assertTrue(RagVectorUtils.keywordScore("白海豚", "中华白海豚记录") > 0.0d);
    }

    @Test
    void utQ01CacheMissingReturnsNull() {
        assertNull(new AssistantQueryCache().get("missing"));
    }

    @Test
    void utQ02CachePutThenGetHits() {
        AssistantQueryCache cache = new AssistantQueryCache();
        AssistantAiDtos.ChatResponse response = chatResponse("ok");
        cache.put("k", response);
        assertSame(response, cache.get("k"));
    }

    @Test
    void utQ03CacheIgnoresNullKey() {
        AssistantQueryCache cache = new AssistantQueryCache();
        cache.put(null, chatResponse("ignored"));
        assertEquals(0, cacheSize(cache));
    }

    @Test
    void utQ04CacheIgnoresNullResponse() {
        AssistantQueryCache cache = new AssistantQueryCache();
        cache.put("k", null);
        assertNull(cache.get("k"));
    }

    @Test
    void utQ05CacheInvalidateAllClearsEntries() {
        AssistantQueryCache cache = new AssistantQueryCache();
        cache.put("k", chatResponse("ok"));
        cache.invalidateAll();
        assertNull(cache.get("k"));
    }

    @Test
    void utQ06CacheOverflowEvictsOldestEntry() {
        AssistantQueryCache cache = new AssistantQueryCache();
        for (int i = 0; i < 257; i++) {
            cache.put("k" + i, chatResponse("r" + i));
        }
        assertNull(cache.get("k0"));
        assertNotNull(cache.get("k256"));
    }

    @Test
    void utQ07ExpiredCacheEntryIsRemoved() throws Exception {
        AssistantQueryCache cache = new AssistantQueryCache();
        Field entriesField = AssistantQueryCache.class.getDeclaredField("entries");
        entriesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Object> entries = (ConcurrentHashMap<String, Object>) entriesField.get(cache);
        Class<?> entryClass = Class.forName("com.gsmv.ai.AssistantQueryCache$CacheEntry");
        Constructor<?> constructor = entryClass.getDeclaredConstructor(AssistantAiDtos.ChatResponse.class, long.class);
        constructor.setAccessible(true);
        entries.put("expired", constructor.newInstance(chatResponse("old"), System.currentTimeMillis() - 1_000));
        assertNull(cache.get("expired"));
        assertFalse(entries.containsKey("expired"));
    }

    @Test
    void utG01GatewayParsesPlainJson() throws Exception {
        JsonNode node = parseJsonContent("{\"confidence\":0.86,\"likelyChineseName\":\"dolphin\"}");
        assertEquals(0.86d, node.path("confidence").asDouble(), 0.000001d);
        assertEquals("dolphin", node.path("likelyChineseName").asText());
    }

    @Test
    void utG02GatewayParsesMarkdownJsonFence() throws Exception {
        JsonNode node = parseJsonContent("```json\n{\"confidence\":0.7}\n```");
        assertEquals(0.7d, node.path("confidence").asDouble(), 0.000001d);
    }

    @Test
    void utG03GatewayParsesLenientJson() throws Exception {
        JsonNode node = parseJsonContent("{confidence:.5, likelyChineseName:'shark',}");
        assertEquals(0.5d, node.path("confidence").asDouble(), 0.000001d);
        assertEquals("shark", node.path("likelyChineseName").asText());
    }

    @Test
    void utG04GatewayExtractsObjectFromArrayLikeText() throws Exception {
        JsonNode node = parseJsonContent("[{\"a\":1}]");
        assertTrue(node.isObject());
        assertEquals(1, node.path("a").asInt());
    }

    @Test
    void utG05GatewayFallbackParsesStructuredText() throws Exception {
        JsonNode node = parseJsonContent("likelyChineseName=大白鲨 confidence=0.91 reasoning='clear image'");
        assertEquals("大白鲨", node.path("likelyChineseName").asText());
        assertEquals(0.91d, node.path("confidence").asDouble(), 0.000001d);
    }

    @Test
    void utG06GatewayThrowsOnUnparseableText() {
        assertThrows(BusinessException.class, () -> parseJsonContent("no structured fields here"));
    }

    @Test
    void utS01IdentifyImageUsesAgentRunEvidenceWithoutServiceRagLookup() throws Exception {
        AiModelGateway aiModelGateway = mock(AiModelGateway.class);
        SpeciesService speciesService = mock(SpeciesService.class);
        RagKnowledgeService ragKnowledgeService = mock(RagKnowledgeService.class);
        AuditService auditService = mock(AuditService.class);
        AgentOrchestratorService agentOrchestratorService = mock(AgentOrchestratorService.class);
        SpeciesAiService service = new SpeciesAiService(
                testAiProperties(),
                speciesService,
                auditService,
                agentOrchestratorService
        );
        ObjectMapper mapper = new ObjectMapper();
        when(aiModelGateway.bailianVisionJson(anyString(), anyString(), any(byte[].class), eq("image/png"))).thenReturn(mapper.readTree("""
                {
                  "likelyChineseName": "Chinese white dolphin",
                  "likelyScientificName": "Sousa chinensis",
                  "confidence": 0.76,
                  "reasoning": "visible dolphin shape",
                  "candidates": [
                    {"chineseName": "Chinese white dolphin", "scientificName": "Sousa chinensis", "confidence": 0.76, "reason": "body shape"}
                  ]
                }
                """));
        when(speciesService.listSpecies(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 1, 5));
        AgentDtos.AgentRunView run = speciesAgentRunWithRagEvidence();
        when(agentOrchestratorService.execute(any(AgentTask.class))).thenReturn(run);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("tester")
                .claim("userId", 7L)
                .claim("displayName", "Tester")
                .claim("authorities", List.of("AI_USE"))
                .build();

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        try {
            MockMultipartFile file = new MockMultipartFile("file", "dolphin.png", "image/png", new byte[] {1, 2, 3});
            SpeciesAiDtos.IdentifyImageResponse response = service.identifyImage(file);

            assertEquals(42L, response.agentRunId());
            assertEquals(1, response.ragEvidence().size());
            assertEquals("Agent verified", response.confidenceLabel());
            assertEquals(0.84d, response.confidence(), 0.000001d);
            verify(agentOrchestratorService).execute(any(AgentTask.class));
            verify(aiModelGateway, never()).bailianVisionJson(anyString(), anyString(), any(byte[].class), anyString());
            verify(ragKnowledgeService, never()).retrieveEvidenceForScenario(any(), any(), anyInt());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void utS02AutocompleteUsesAgentWorkflowWithoutServiceModelOrRagLookup() {
        AiModelGateway aiModelGateway = mock(AiModelGateway.class);
        SpeciesService speciesService = mock(SpeciesService.class);
        RagKnowledgeService ragKnowledgeService = mock(RagKnowledgeService.class);
        AuditService auditService = mock(AuditService.class);
        AgentOrchestratorService agentOrchestratorService = mock(AgentOrchestratorService.class);
        SpeciesAiService service = new SpeciesAiService(
                testAiProperties(),
                speciesService,
                auditService,
                agentOrchestratorService
        );
        when(speciesService.listSpecies(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 1, 5));
        when(agentOrchestratorService.execute(any(AgentTask.class))).thenReturn(speciesProfileAgentRun());
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("tester")
                .claim("userId", 7L)
                .claim("displayName", "Tester")
                .claim("authorities", List.of("AI_USE"))
                .build();

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        try {
            SpeciesAiDtos.AutocompleteResponse response = service.autocomplete(new SpeciesAiDtos.AutocompleteRequest(
                    "Chinese white dolphin",
                    "Sousa chinensis",
                    "",
                    "",
                    "",
                    "",
                    "",
                    ""
            ));

            assertEquals(43L, response.agentRunId());
            assertEquals("Chinese white dolphin", response.chineseName());
            assertEquals("Sousa chinensis", response.scientificName());
            assertEquals(0.82d, response.confidence(), 0.000001d);
            assertEquals("VERIFIED", response.verificationStatus());
            verify(agentOrchestratorService).execute(any(AgentTask.class));
            verify(aiModelGateway, never()).deepSeekJson(any());
            verify(ragKnowledgeService, never()).retrieveForScenario(any(), any(), anyInt());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void utO01ObservationRuleTagsCompleteInput() throws Exception {
        List<String> tags = buildRuleTags(completeObservation(LocalDateTime.of(2026, 5, 27, 10, 0), 31.0d, 25.0d, 6.0d, 6.0d, 1));
        assertTrue(tags.size() >= 3);
        assertTrue(tags.contains("湛江近海"));
    }

    @Test
    void utO02ObservationRuleTagsSummerDay() throws Exception {
        List<String> tags = buildRuleTags(completeObservation(LocalDateTime.of(2026, 7, 1, 12, 0), 31.0d, 25.0d, 6.0d, 6.0d, 1));
        assertTrue(tags.size() >= 3);
    }

    @Test
    void utO03ObservationRuleTagsWinterNight() throws Exception {
        List<String> tags = buildRuleTags(completeObservation(LocalDateTime.of(2026, 1, 1, 23, 0), 31.0d, 25.0d, 6.0d, 6.0d, 1));
        assertTrue(tags.size() >= 3);
    }

    @Test
    void utO04ObservationRuleTagsEnvironmentExtremes() throws Exception {
        List<String> tags = buildRuleTags(completeObservation(LocalDateTime.of(2026, 5, 27, 10, 0), 36.0d, 29.0d, 4.0d, 40.0d, 1));
        assertTrue(tags.size() >= 6);
    }

    @Test
    void utO05ObservationRuleTagsMultipleSpecies() throws Exception {
        List<String> tags = buildRuleTags(completeObservation(LocalDateTime.of(2026, 5, 27, 10, 0), 31.0d, 25.0d, 6.0d, 6.0d, 2));
        assertTrue(tags.size() >= 4);
    }

    @Test
    void utO06ObservationEnvironmentEmptyTrue() throws Exception {
        assertEquals(Boolean.TRUE, invokeObservationPrivate("isEnvironmentEmpty", new Class<?>[]{ObservationAiDtos.EnvironmentSnapshot.class},
                new ObservationAiDtos.EnvironmentSnapshot(null, null, null, null, null, null, null, null)));
    }

    @Test
    void utO07ObservationEnvironmentEmptyFalse() throws Exception {
        assertEquals(Boolean.FALSE, invokeObservationPrivate("isEnvironmentEmpty", new Class<?>[]{ObservationAiDtos.EnvironmentSnapshot.class},
                new ObservationAiDtos.EnvironmentSnapshot(BigDecimal.valueOf(25), null, null, null, null, null, null, null)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void utO08ObservationRuleBaselineCreatesReviewNotesAndSummary() throws Exception {
        ObservationAiDtos.AnalyzeObservationRequest request = new ObservationAiDtos.AnalyzeObservationRequest(
                1L,
                "Reef",
                LocalDateTime.of(2026, 6, 9, 10, 0),
                BigDecimal.valueOf(21.0d),
                BigDecimal.valueOf(110.0d),
                "",
                "",
                null,
                List.of()
        );

        List<String> notes = (List<String>) invokeObservationPrivate(
                "buildRuleReviewNotes",
                new Class<?>[]{ObservationAiDtos.AnalyzeObservationRequest.class, List.class},
                request,
                List.of()
        );
        String summary = (String) invokeObservationPrivate(
                "buildRuleObservationSummary",
                new Class<?>[]{ObservationAiDtos.AnalyzeObservationRequest.class, List.class},
                request,
                List.of()
        );

        assertTrue(notes.size() >= 3);
        assertTrue(summary.contains("关联物种 0 个"));
    }

    @Test
    void utA01AssistantTreatsWhatIsSpeciesAsProfileQuestion() throws Exception {
        assertEquals("species_profile", inferAssistantIntent("中华白海豚是什么", "中华白海豚"));
    }

    @Test
    void utA02AssistantKeepsDistributionQuestionAsSpeciesLookup() throws Exception {
        assertEquals("species_lookup", inferAssistantIntent("中华白海豚分布在哪里", "中华白海豚"));
    }

    @Test
    void utA03AssistantTreatsCasualFoodQuestionAsGeneralChat() throws Exception {
        assertEquals("general_chat", inferAssistantIntent("鲍鱼好吃吗", "鲍鱼"));
    }

    @Test
    void utA04AssistantTreatsOpenLocationFoodQuestionAsGeneralChat() throws Exception {
        assertEquals("general_chat", inferAssistantIntent("阳江的鲍鱼怎么样", "鲍鱼"));
    }

    @Test
    void utA05AssistantKeepsObservationQuestionAsObservationLookup() throws Exception {
        assertEquals("observation_lookup", inferAssistantIntent("鲍鱼有哪些观测记录", "鲍鱼"));
    }

    @Test
    void utA06AssistantTreatsUnrelatedDailyQuestionAsGeneralChat() throws Exception {
        assertEquals("general_chat", inferAssistantIntent("最近有什么电影好看", null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void utA07AssistantMergesAgentFinalOutputEvidence() throws Exception {
        AssistantAiService service = new AssistantAiService(null, null, null, null, null, null, null, null, null, null);
        Method method = AssistantAiService.class.getDeclaredMethod("mergeAgentEvidence", List.class, Object.class);
        method.setAccessible(true);
        List<AssistantAiDtos.EvidenceItem> original = List.of(new AssistantAiDtos.EvidenceItem(
                "local",
                "本地证据",
                "本地回答证据"
        ));
        Map<String, Object> finalOutput = Map.of(
                "evidenceSnapshot",
                List.of(Map.of(
                        "type", "SYSTEM_SPECIES",
                        "sourceType", "SPECIES",
                        "sourceId", 3L,
                        "title", "中华白海豚",
                        "description", "系统物种档案证据",
                        "score", 0.92d,
                        "sourcePath", "/species/3"
                ))
        );

        List<AssistantAiDtos.EvidenceItem> merged =
                (List<AssistantAiDtos.EvidenceItem>) method.invoke(service, original, finalOutput);

        assertEquals(2, merged.size());
        assertTrue(merged.stream().anyMatch(item ->
                "中华白海豚".equals(item.title())
                        && Long.valueOf(3L).equals(item.sourceId())
                        && "/species/3".equals(item.sourcePath())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void utR01ReportServiceAppliesAgentFinalDraft() throws Exception {
        AiReportService service = new AiReportService(null, null, null, null, new ObjectMapper(), null);
        Class<?> generatedReportClass = Arrays.stream(AiReportService.class.getDeclaredClasses())
                .filter(type -> "GeneratedReport".equals(type.getSimpleName()))
                .findFirst()
                .orElseThrow();
        Constructor<?> constructor = generatedReportClass.getDeclaredConstructor(
                String.class,
                String.class,
                List.class,
                List.class,
                List.class,
                List.class
        );
        constructor.setAccessible(true);
        Object fallback = constructor.newInstance(
                "基础标题",
                "基础摘要",
                List.of("基础发现"),
                List.of("基础风险"),
                List.of("基础建议"),
                List.of("基础证据")
        );
        Method method = AiReportService.class.getDeclaredMethod("applyAgentDraft", generatedReportClass, Object.class);
        method.setAccessible(true);

        Object applied = method.invoke(service, fallback, Map.of(
                "finalDraft",
                Map.of(
                        "title", "Agent 标题",
                        "summary", "Agent 摘要",
                        "highlights", List.of("Agent 发现"),
                        "risks", List.of("Agent 风险"),
                        "recommendations", List.of("Agent 建议"),
                        "evidence", List.of("Agent 证据")
                )
        ));

        Method titleMethod = generatedReportClass.getDeclaredMethod("title");
        Method summaryMethod = generatedReportClass.getDeclaredMethod("summary");
        Method highlightsMethod = generatedReportClass.getDeclaredMethod("highlights");
        Method recommendationsMethod = generatedReportClass.getDeclaredMethod("recommendations");
        titleMethod.setAccessible(true);
        summaryMethod.setAccessible(true);
        highlightsMethod.setAccessible(true);
        recommendationsMethod.setAccessible(true);
        assertEquals("Agent 标题", titleMethod.invoke(applied));
        assertEquals("Agent 摘要", summaryMethod.invoke(applied));
        assertEquals(List.of("Agent 发现"), (List<String>) highlightsMethod.invoke(applied));
        assertEquals(List.of("Agent 建议"), (List<String>) recommendationsMethod.invoke(applied));
    }

    @Test
    @SuppressWarnings("unchecked")
    void utR02ReportBaselineDoesNotRequireAiOrRagDependencies() throws Exception {
        ReportService reportService = mock(ReportService.class);
        when(reportService.dashboardSummary()).thenReturn(new DashboardSummary(8, 16, 3, 2, 4));
        when(reportService.observationTrend(30)).thenReturn(List.of(new NameValuePoint("2026-06", 6)));
        when(reportService.ecosystemAnalytics()).thenReturn(List.of(new EcosystemAnalyticsPoint(1L, "Reef", "MARINE", 7, 4)));
        when(reportService.protectionLevelDistribution()).thenReturn(List.of(new NameValuePoint("PROTECTED", 3)));
        AiReportService service = new AiReportService(null, reportService, null, null, new ObjectMapper(), null);
        Method method = AiReportService.class.getDeclaredMethod("generateBaselineReport", String.class, int.class);
        method.setAccessible(true);

        Object generated = method.invoke(service, "MONTHLY", 30);

        Class<?> generatedReportClass = generated.getClass();
        Method titleMethod = generatedReportClass.getDeclaredMethod("title");
        Method summaryMethod = generatedReportClass.getDeclaredMethod("summary");
        Method highlightsMethod = generatedReportClass.getDeclaredMethod("highlights");
        Method evidenceMethod = generatedReportClass.getDeclaredMethod("evidence");
        titleMethod.setAccessible(true);
        summaryMethod.setAccessible(true);
        highlightsMethod.setAccessible(true);
        evidenceMethod.setAccessible(true);
        assertNotNull(titleMethod.invoke(generated));
        assertFalse(String.valueOf(summaryMethod.invoke(generated)).isBlank());
        assertFalse(((List<String>) highlightsMethod.invoke(generated)).isEmpty());
        assertTrue(((List<String>) evidenceMethod.invoke(generated)).stream().noneMatch(item -> item.contains("RAG")));
    }

    private AiProperties testAiProperties() {
        return new AiProperties(
                new AiProperties.Bailian(true, "test-key", "http://localhost", "vision", "embedding", 1024),
                new AiProperties.Ollama(false, "http://localhost:11434", "bge-m3", 1024),
                new AiProperties.DeepSeek(false, "test-key", "http://localhost", "chat"),
                new AiProperties.Embedding("ollama"),
                0.7d,
                60,
                20
        );
    }

    private AgentDtos.AgentRunView speciesAgentRunWithRagEvidence() {
        List<Map<String, Object>> candidates = List.of(Map.of(
                "chineseName", "Chinese white dolphin",
                "scientificName", "Sousa chinensis",
                "confidence", 0.76d,
                "reason", "body shape"
        ));
        AgentDtos.AgentStepView visionStep = new AgentDtos.AgentStepView(
                1L,
                42L,
                2,
                "Vision Review Agent",
                "Vision review",
                "SUCCESS",
                "recognized candidate",
                Map.of(),
                Map.of(
                        "likelyChineseName", "Chinese white dolphin",
                        "likelyScientificName", "Sousa chinensis",
                        "confidence", 0.76d,
                        "needsHumanReview", false,
                        "reasoning", "visible dolphin shape",
                        "candidates", candidates,
                        "candidateSummaries", candidates
                ),
                List.of(),
                null,
                0.76d,
                8L,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        List<Map<String, Object>> ragEvidence = List.of(Map.of(
                "type", "RAG",
                "sourceType", "SPECIES",
                "sourceId", 33L,
                "documentId", 11L,
                "chunkId", 22L,
                "title", "Sousa chinensis profile",
                "summary", "Chinese white dolphin facts",
                "score", 0.91d,
                "sourcePath", "/species/33"
        ));
        AgentDtos.AgentStepView ragStep = new AgentDtos.AgentStepView(
                2L,
                42L,
                3,
                "RAG Evidence Agent",
                "Evidence",
                "SUCCESS",
                "retrieved evidence",
                Map.of(),
                Map.of("hitCount", 1),
                ragEvidence,
                null,
                0.9d,
                8L,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        return new AgentDtos.AgentRunView(
                42L,
                AgentOrchestratorService.WORKFLOW_SPECIES_IDENTIFY,
                "SUCCESS",
                "IMAGE",
                null,
                7L,
                "tester",
                "Chinese white dolphin Sousa chinensis",
                "verified by agents",
                "VERIFIED",
                0.84d,
                Map.of(
                        "riskFindings", List.of(),
                        "verifierOutput", Map.of("reviewFindings", List.of())
                ),
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                List.of(visionStep, ragStep)
        );
    }

    private AgentDtos.AgentRunView speciesProfileAgentRun() {
        Map<String, Object> taxonomyOutput = new LinkedHashMap<>();
        taxonomyOutput.put("chineseName", "Chinese white dolphin");
        taxonomyOutput.put("scientificName", "Sousa chinensis");
        taxonomyOutput.put("phylumName", "Chordata");
        taxonomyOutput.put("className", "Mammalia");
        taxonomyOutput.put("orderName", "Cetacea");
        taxonomyOutput.put("familyName", "Delphinidae");
        taxonomyOutput.put("genusName", "Sousa");
        taxonomyOutput.put("description", "Coastal dolphin species");
        taxonomyOutput.put("summary", "Profile draft");
        taxonomyOutput.put("confidence", 0.82d);
        taxonomyOutput.put("notes", List.of("agent generated"));
        AgentDtos.AgentStepView taxonomyStep = new AgentDtos.AgentStepView(
                11L,
                43L,
                3,
                "Taxonomy Agent",
                "Species profile assist",
                "SUCCESS",
                "generated profile",
                Map.of(),
                taxonomyOutput,
                List.of(),
                null,
                0.82d,
                9L,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        return new AgentDtos.AgentRunView(
                43L,
                AgentOrchestratorService.WORKFLOW_SPECIES_PROFILE_ASSIST,
                "SUCCESS",
                "SPECIES_PROFILE",
                null,
                7L,
                "tester",
                "Chinese white dolphin",
                "verified species profile assist",
                "VERIFIED",
                0.82d,
                Map.of("verifierOutput", Map.of("reviewFindings", List.of())),
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                List.of(taxonomyStep)
        );
    }

    private AssistantAiDtos.ChatResponse chatResponse(String answer) {
        return new AssistantAiDtos.ChatResponse(answer, null, List.of(), List.of(), false);
    }

    private int cacheSize(AssistantQueryCache cache) {
        try {
            Field entriesField = AssistantQueryCache.class.getDeclaredField("entries");
            entriesField.setAccessible(true);
            return ((Map<?, ?>) entriesField.get(cache)).size();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private JsonNode parseJsonContent(String content) throws Exception {
        AiProperties properties = new AiProperties(
                new AiProperties.Bailian(false, "", "http://localhost", "vision", "embedding", 1024),
                new AiProperties.Ollama(true, "http://localhost:11434", "bge-m3", 1024),
                new AiProperties.DeepSeek(false, "", "http://localhost", "chat"),
                new AiProperties.Embedding("ollama"),
                0.68d,
                60,
                20
        );
        AiModelGateway gateway = new AiModelGateway(properties, RestClient.builder(), new ObjectMapper());
        Method method = AiModelGateway.class.getDeclaredMethod("parseJsonContent", String.class);
        method.setAccessible(true);
        try {
            Object result = method.invoke(gateway, content);
            return assertInstanceOf(JsonNode.class, result);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof BusinessException businessException) {
                throw businessException;
            }
            throw ex;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> buildRuleTags(ObservationAiDtos.AnalyzeObservationRequest request) throws Exception {
        return (List<String>) invokeObservationPrivate("buildRuleTags", new Class<?>[]{ObservationAiDtos.AnalyzeObservationRequest.class}, request);
    }

    private Object invokeObservationPrivate(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        ObservationAiService service = new ObservationAiService(null, null, null, new ObjectMapper(), null);
        Method method = ObservationAiService.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(service, args);
    }

    private String inferAssistantIntent(String message, String speciesKeyword) throws Exception {
        AssistantAiService service = new AssistantAiService(null, null, null, null, null, null, null, null, null, null);
        Method method = AssistantAiService.class.getDeclaredMethod(
                "inferIntent",
                String.class,
                String.class,
                String.class,
                boolean.class,
                boolean.class
        );
        method.setAccessible(true);
        return (String) method.invoke(service, message, speciesKeyword, null, false, false);
    }

    private ObservationAiDtos.AnalyzeObservationRequest completeObservation(
            LocalDateTime observedAt,
            double salinity,
            double waterTemperature,
            double dissolvedOxygen,
            double depth,
            int speciesCount
    ) {
        List<ObservationAiDtos.SpeciesObservationItem> species = new ArrayList<>();
        for (int i = 0; i < speciesCount; i++) {
            species.add(new ObservationAiDtos.SpeciesObservationItem(null, "Sousa chinensis", "中华白海豚", 3, "foraging", "ok"));
        }
        return new ObservationAiDtos.AnalyzeObservationRequest(
                1L,
                "湛江近海",
                observedAt,
                BigDecimal.valueOf(21.18d),
                BigDecimal.valueOf(110.53d),
                "东里海草床",
                "note",
                new ObservationAiDtos.EnvironmentSnapshot(
                        BigDecimal.valueOf(waterTemperature),
                        BigDecimal.valueOf(salinity),
                        BigDecimal.valueOf(8.1d),
                        BigDecimal.valueOf(dissolvedOxygen),
                        BigDecimal.valueOf(4.2d),
                        BigDecimal.valueOf(depth),
                        "sunny",
                        "calm"
                ),
                species
        );
    }
}
