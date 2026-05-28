package com.gsmv.ai;

import com.gsmv.ai.dto.AssistantAiDtos;
import com.gsmv.ai.history.AssistantChatHistoryService;
import com.gsmv.ai.rag.RagKnowledgeService;
import com.gsmv.ai.rag.RagSearchHit;
import com.gsmv.audit.service.AuditService;
import com.gsmv.common.PageResponse;
import com.gsmv.observation.ObservationService;
import com.gsmv.observation.dto.ObservationDetailView;
import com.gsmv.observation.dto.ObservationSpeciesView;
import com.gsmv.observation.dto.ObservationView;
import com.gsmv.report.ReportService;
import com.gsmv.report.dto.DashboardSummary;
import com.gsmv.report.dto.EcosystemAnalyticsPoint;
import com.gsmv.security.SecurityUtils;
import com.gsmv.species.SpeciesService;
import com.gsmv.species.dto.SpeciesDetailView;
import com.gsmv.species.dto.SpeciesView;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssistantAiService {

    private static final Set<String> RISK_STATUSES = Set.of("VU", "EN", "CR", "EW", "EX");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern DAYS_PATTERN = Pattern.compile("(?:最近|近|过去)(\\d{1,3})天");
    private static final Pattern YEARS_PATTERN = Pattern.compile("(?:最近|近|过去)(\\d{1,2})年");
    private static final Pattern LOCATION_TERM_PATTERN = Pattern.compile("([\\p{IsHan}A-Za-z]{2,16}(?:北部|南部|东部|西部|中部|附近|周边|近海|沿海|海域|海湾|海峡|河口|半岛|群岛|样带|红树林|海草床|珊瑚礁))");
    private static final Map<String, Integer> CHINESE_YEAR_WORDS = Map.ofEntries(
            Map.entry("一年", 1),
            Map.entry("两年", 2),
            Map.entry("二年", 2),
            Map.entry("三年", 3),
            Map.entry("四年", 4),
            Map.entry("五年", 5),
            Map.entry("六年", 6),
            Map.entry("七年", 7),
            Map.entry("八年", 8),
            Map.entry("九年", 9),
            Map.entry("十年", 10)
    );
    private static final Map<String, List<String>> LOCATION_ALIAS_GROUPS = Map.ofEntries(
            Map.entry("湛江", List.of("湛江", "湛江附近", "湛江周边", "湛江近海", "雷州半岛")),
            Map.entry("南海北部", List.of("南海北部", "南海北部海域", "南海北部近海")),
            Map.entry("近岸样带", List.of("近岸样带", "近岸样带区", "近岸样带调查带", "样带")),
            Map.entry("珠江口", List.of("珠江口", "伶仃洋")),
            Map.entry("红树林", List.of("红树林", "红树林生态系统")),
            Map.entry("海草床", List.of("海草床", "海草床生态系统")),
            Map.entry("珊瑚礁", List.of("珊瑚礁", "珊瑚礁生态系统"))
    );
    private static final List<String> TREND_KEYWORDS = List.of("趋势", "变化", "波动", "增长", "下降", "对比", "演变");
    private static final List<String> RISK_KEYWORDS = List.of("濒危", "易危", "受威胁", "重点保护", "高保护", "保护等级较高");
    private static final List<String> ACTIVITY_KEYWORDS = List.of("谁最活跃", "最活跃", "观测次数", "观测活动", "谁的观测", "观察活动");
    private static final List<String> ECOSYSTEM_HINTS = List.of("珊瑚礁", "红树林", "海草床", "深海", "近海", "海湾", "河口", "海域");
    private static final List<String> LOCATION_SUFFIXES = List.of("附近", "周边", "近海", "沿海", "海域", "海湾", "海峡", "样带");
    private static final List<String> MAP_SCOPE_KEYWORDS = List.of(
            "地图覆盖", "地图涵盖", "覆盖范围", "涵盖范围", "什么范围", "哪些区域", "哪些海域", "哪里有点位", "地图上都有哪些",
            "地图里有什么", "覆盖到哪", "分布在哪些地方"
    );
    private static final List<String> HELP_KEYWORDS = List.of(
            "你能做什么", "可以问什么", "怎么问", "怎么用", "能查什么", "你会什么", "你能帮我什么"
    );
    private static final Set<String> CLARIFY_ONLY_MESSAGES = Set.of(
            "?", "？", "啥", "什么", "什么意思", "然后呢", "继续", "展开", "详细点", "再说说", "具体呢"
    );
    private static final List<String> FOLLOW_UP_HINTS = List.of(
            "只看", "只筛", "换成", "改成", "按", "那", "那就", "那现在", "这里", "这个范围", "这个区域",
            "再看", "继续看", "近30天", "近三年", "最近", "现在呢", "然后呢", "详细点", "展开点位"
    );
    private static final List<String> SPECIES_PROFILE_KEYWORDS = List.of(
            "是什么", "是啥", "介绍", "介绍一下", "科普", "了解一下", "讲讲", "说说", "简介", "资料", "信息"
    );
    private static final List<String> SPECIES_OBSERVATION_KEYWORDS = List.of(
            "观测", "记录", "最近", "近30天", "近三年", "活跃", "出现", "发现", "看到"
    );
    private static final List<String> SYSTEM_DATA_KEYWORDS = List.of(
            "系统", "数据库", "档案", "记录", "观测", "点位", "地图", "分布", "范围", "哪里", "在哪",
            "保护等级", "IUCN", "濒危", "易危", "国家一级", "国家二级", "栖息", "栖息地", "形态", "习性",
            "生态", "趋势", "统计", "数量", "有哪些", "列出", "筛选"
    );
    private static final List<String> EXPLICIT_SYSTEM_DATA_KEYWORDS = List.of(
            "系统", "数据库", "档案", "记录", "观测", "点位", "地图", "保护等级", "IUCN", "濒危", "易危",
            "国家一级", "国家二级", "分布", "栖息", "形态", "习性", "数量", "趋势", "统计", "筛选"
    );
    private static final List<String> CASUAL_GENERAL_KEYWORDS = List.of(
            "好吃", "吃吗", "能吃", "怎么吃", "做法", "口感", "味道", "价格", "多少钱", "哪里买",
            "怎么样", "好不好", "推荐", "可以吗", "能不能", "聊天", "笑话", "天气", "电影", "旅游"
    );

    private final AiProperties aiProperties;
    private final ObservationService observationService;
    private final SpeciesService speciesService;
    private final ReportService reportService;
    private final AuditService auditService;
    private final AssistantQueryCache assistantQueryCache;
    private final RagKnowledgeService ragKnowledgeService;
    private final AiModelGateway aiModelGateway;
    private final AssistantChatHistoryService assistantChatHistoryService;

    public AssistantAiService(
            AiProperties aiProperties,
            ObservationService observationService,
            SpeciesService speciesService,
            ReportService reportService,
            AuditService auditService,
            AssistantQueryCache assistantQueryCache,
            RagKnowledgeService ragKnowledgeService,
            AiModelGateway aiModelGateway,
            AssistantChatHistoryService assistantChatHistoryService
    ) {
        this.aiProperties = aiProperties;
        this.observationService = observationService;
        this.speciesService = speciesService;
        this.reportService = reportService;
        this.auditService = auditService;
        this.assistantQueryCache = assistantQueryCache;
        this.ragKnowledgeService = ragKnowledgeService;
        this.aiModelGateway = aiModelGateway;
        this.assistantChatHistoryService = assistantChatHistoryService;
    }

    public AssistantAiDtos.ChatResponse chat(AssistantAiDtos.ChatRequest request) {
        Long currentUserId = SecurityUtils.requireCurrentUser().userId();
        String cacheKey = currentUserId + "::" + buildCacheKey(request);
        AssistantAiDtos.ChatResponse cachedResponse = assistantQueryCache.get(cacheKey);
        if (cachedResponse != null) {
            AssistantAiDtos.ChatResponse response = withCacheHit(cachedResponse, true);
            assistantChatHistoryService.recordExchange(request, response);
            auditService.record(
                    currentUserId,
                    "AI",
                    "ASSISTANT_CHAT",
                    "ASSISTANT",
                    null,
                    true,
                    "{\"message\":\"" + escapeJson(request.message()) + "\",\"cached\":true}"
            );
            return response;
        }

        AssistantAiDtos.StructuredQuery plan = extractStructuredQueryFast(request.message(), request.history());
        AssistantAiDtos.ChatResponse response;
        if (isLightweightIntent(plan.intent())) {
            response = buildLightweightResponse(plan, request);
        } else {
            AssistantContext context = collectContext(plan);
            List<RagSearchHit> ragHits = ragKnowledgeService.retrieveForScenario(RagKnowledgeService.SCENARIO_ASSISTANT, request.message(), 6);
            response = buildConversationalResponse(request, plan, context, ragHits);
        }
        assistantChatHistoryService.recordExchange(request, response);
        assistantQueryCache.put(cacheKey, response);

        auditService.record(
                currentUserId,
                "AI",
                "ASSISTANT_CHAT",
                "ASSISTANT",
                null,
                true,
                "{\"message\":\"" + escapeJson(request.message()) + "\",\"cached\":false}"
        );
        return response;
    }

    private AssistantAiDtos.ChatResponse withCacheHit(AssistantAiDtos.ChatResponse response, boolean cacheHit) {
        return new AssistantAiDtos.ChatResponse(
                response.answer(),
                response.structuredQuery(),
                response.highlights(),
                response.evidence(),
                cacheHit
        );
    }

    private AssistantAiDtos.ChatResponse enrichWithRag(AssistantAiDtos.ChatResponse response, String question) {
        List<RagSearchHit> hits = ragKnowledgeService.retrieveForScenario(RagKnowledgeService.SCENARIO_ASSISTANT, question, 6);
        if (hits.isEmpty()) {
            return response;
        }
        List<AssistantAiDtos.EvidenceItem> evidence = new ArrayList<>(response.evidence());
        hits.stream().limit(4).forEach(hit -> evidence.add(new AssistantAiDtos.EvidenceItem(
                "rag:" + hit.sourceType().toLowerCase(Locale.ROOT),
                hit.title(),
                firstNonBlank(hit.summary(), truncateForReply(hit.content(), 120)),
                hit.sourceId(),
                hit.score(),
                hit.sourcePath()
        )));
        List<String> highlights = new ArrayList<>(response.highlights());
        highlights.add("RAG 知识库命中 " + hits.size() + " 条证据");
        String ragSummary = hits.stream()
                .limit(2)
                .map(hit -> "《" + hit.title() + "》")
                .collect(Collectors.joining("、"));
        String answer = response.answer()
                + " 知识库补充证据主要来自 "
                + ragSummary
                + "，可在右侧证据区继续查看来源。";
        return new AssistantAiDtos.ChatResponse(
                answer,
                response.structuredQuery(),
                highlights.stream().limit(5).toList(),
                evidence.stream().limit(10).toList(),
                response.cacheHit()
        );
    }

    private AssistantAiDtos.ChatResponse buildConversationalResponse(
            AssistantAiDtos.ChatRequest request,
            AssistantAiDtos.StructuredQuery plan,
            AssistantContext context,
            List<RagSearchHit> ragHits
    ) {
        AssistantAiDtos.ChatResponse localResponse = buildLocalResponse(plan, context);
        List<AssistantAiDtos.EvidenceItem> evidence = mergeEvidence(localResponse.evidence(), ragHits);
        List<String> highlights = buildConversationalHighlights(localResponse.highlights(), ragHits);

        try {
            String answer = aiModelGateway.deepSeekText(buildConversationMessages(request, plan, context, ragHits));
            if (!StringUtils.hasText(answer)) {
                answer = softenLocalAnswer(localResponse.answer());
            }
            return new AssistantAiDtos.ChatResponse(
                    normalizeAssistantAnswer(answer),
                    plan,
                    highlights,
                    evidence,
                    false
            );
        } catch (RuntimeException ignored) {
            return new AssistantAiDtos.ChatResponse(
                    softenLocalAnswer(localResponse.answer()),
                    plan,
                    highlights,
                    evidence,
                    false
            );
        }
    }

    private List<Map<String, Object>> buildConversationMessages(
            AssistantAiDtos.ChatRequest request,
            AssistantAiDtos.StructuredQuery plan,
            AssistantContext context,
            List<RagSearchHit> ragHits
    ) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(AiModelGateway.message("system", """
                你是 GSMV 海洋生物多样性管理台里的 AI 助手，但回答要像正常对话助手一样自然。
                直接回答用户问题，不要模板化，不要总说“按当前筛选条件”“系统中匹配到”。
                用户问“介绍一下/是什么/某个物种名”时，默认按科普介绍来回答：先说明它是什么，再讲特征、分布、保护状态和系统内相关记录。
                系统数据和 RAG 证据是参考资料，不要机械罗列；可以自然地说“我这里查到...”。
                如果用户问的是日常、常识、口味、闲聊或开放式问题，要像通用 DeepSeek 助手一样直接回答，不要强行转成系统统计。
                如果资料不足，可以结合通用海洋生物知识回答，但要避免编造具体系统记录。
                回答使用中文，语气专业、顺口、像 GPT 一样会解释；一般控制在 2 到 5 个短段落。
                """));

        List<AssistantAiDtos.ConversationMessage> history = request.history() == null ? List.of() : request.history();
        history.stream()
                .filter(item -> item != null && StringUtils.hasText(item.role()) && StringUtils.hasText(item.content()))
                .skip(Math.max(0, history.size() - 6))
                .forEach(item -> messages.add(AiModelGateway.message(normalizeRole(item.role()), truncateForPrompt(item.content(), 700))));

        messages.add(AiModelGateway.message("user", """
                用户问题：
                %s

                结构化理解：
                %s

                系统数据摘要：
                %s

                RAG 检索证据：
                %s

                请基于这些资料自然回答。不要输出 JSON，不要写“根据结构化查询”，不要把右侧证据区当成正文重复说明。
                """.formatted(
                request.message(),
                describePlan(plan),
                buildContextForPrompt(plan, context),
                buildRagForPrompt(ragHits)
        )));
        return messages;
    }

    private List<AssistantAiDtos.EvidenceItem> mergeEvidence(
            List<AssistantAiDtos.EvidenceItem> localEvidence,
            List<RagSearchHit> ragHits
    ) {
        List<AssistantAiDtos.EvidenceItem> evidence = new ArrayList<>();
        if (localEvidence != null) {
            evidence.addAll(localEvidence.stream().limit(5).toList());
        }
        if (ragHits != null) {
            ragHits.stream().limit(6).forEach(hit -> evidence.add(new AssistantAiDtos.EvidenceItem(
                    "rag:" + safe(hit.sourceType()).toLowerCase(Locale.ROOT),
                    hit.title(),
                    firstNonBlank(hit.summary(), truncateForReply(hit.content(), 140)),
                    hit.sourceId(),
                    hit.score(),
                    hit.sourcePath()
            )));
        }
        return evidence.stream().limit(10).toList();
    }

    private List<String> buildConversationalHighlights(List<String> localHighlights, List<RagSearchHit> ragHits) {
        List<String> highlights = new ArrayList<>();
        if (localHighlights != null) {
            highlights.addAll(localHighlights.stream().limit(3).toList());
        }
        if (ragHits != null && !ragHits.isEmpty()) {
            highlights.add("参考了 " + ragHits.size() + " 条 RAG 证据");
        }
        return highlights.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .limit(4)
                .toList();
    }

    private String buildContextForPrompt(AssistantAiDtos.StructuredQuery plan, AssistantContext context) {
        List<String> lines = new ArrayList<>();
        if ("general_chat".equals(safe(plan.intent()))
                && context.species().isEmpty()
                && context.observationViews().isEmpty()
                && context.ecosystemAnalytics().isEmpty()) {
            return "本次问题没有命中必须引用的系统业务数据。请优先按通用问答自然回答，RAG 证据仅作为可选参考。";
        }
        if ("general_chat".equals(safe(plan.intent()))) {
            lines.add("本次问题偏通用问答；下面资料仅供参考，不要把回答写成系统统计。");
        }
        lines.add("系统概况：物种档案 " + context.dashboardSummary().totalSpecies()
                + " 条，观测记录 " + context.dashboardSummary().totalObservations()
                + " 条，生态系统 " + context.dashboardSummary().totalEcosystems() + " 个。");
        if (!context.species().isEmpty()) {
            lines.add("相关物种：");
            context.species().stream().limit(5).forEach(item -> lines.add("- "
                    + firstNonBlank(item.chineseName(), item.scientificName(), "未命名物种")
                    + "；学名：" + firstNonBlank(item.scientificName(), "未填写")
                    + "；分类：" + firstNonBlank(item.classificationPath(), "未填写")
                    + "；保护等级：" + firstNonBlank(item.protectionLevel(), "未标注")
                    + "；IUCN：" + firstNonBlank(item.iucnStatus(), "未标注")
                    + "；分布：" + firstNonBlank(item.geoRangeText(), item.distribution(), "未填写")
                    + "；形态：" + truncateForPrompt(firstNonBlank(item.morphology(), item.description()), 220)
                    + "；习性：" + truncateForPrompt(firstNonBlank(item.habit(), item.habitat()), 220)));
        }
        if (!context.observationViews().isEmpty()) {
            lines.add("相关观测：");
            context.observationViews().stream().limit(6).forEach(item -> lines.add("- "
                    + firstNonBlank(item.locationName(), item.ecosystemName(), "未命名点位")
                    + "；时间：" + formatDate(item.observedAt())
                    + "；生态系统：" + firstNonBlank(item.ecosystemName(), "未标注")
                    + "；观测人员：" + firstNonBlank(item.observerName(), "未标注")));
        }
        if (!context.ecosystemAnalytics().isEmpty()) {
            lines.add("生态系统统计：" + context.ecosystemAnalytics().stream()
                    .limit(5)
                    .map(item -> item.ecosystemName() + " " + item.observationCount() + " 次观测/" + item.speciesCount() + " 种")
                    .collect(Collectors.joining("；")));
        }
        if (!context.trendPoints().isEmpty()) {
            lines.add("趋势：" + context.trendPoints().stream()
                    .limit(8)
                    .map(item -> item.month() + " 观测" + item.observationCount() + "次/物种" + item.speciesCount() + "种")
                    .collect(Collectors.joining("；")));
        }
        return String.join("\n", lines);
    }

    private String buildRagForPrompt(List<RagSearchHit> ragHits) {
        if (ragHits == null || ragHits.isEmpty()) {
            return "未检索到强相关 RAG 证据。";
        }
        return ragHits.stream()
                .limit(6)
                .map(hit -> "- 来源：" + hit.sourceType()
                        + "；标题：" + hit.title()
                        + "；相似度：" + String.format(Locale.ROOT, "%.2f", hit.score())
                        + "；摘要：" + truncateForPrompt(firstNonBlank(hit.summary(), hit.content()), 360))
                .collect(Collectors.joining("\n"));
    }

    private String describePlan(AssistantAiDtos.StructuredQuery plan) {
        return "意图=" + safe(plan.intent())
                + "；地点=" + firstNonBlank(plan.locationKeyword(), "无")
                + "；生态系统=" + firstNonBlank(plan.ecosystemKeyword(), "无")
                + "；物种=" + firstNonBlank(plan.speciesKeyword(), "无")
                + "；保护等级=" + firstNonBlank(plan.protectionLevel(), "无")
                + "；IUCN=" + firstNonBlank(plan.iucnStatus(), "无")
                + "；时间=" + (plan.recentDays() != null ? "近" + plan.recentDays() + "天" : plan.yearsBack() != null ? "近" + plan.yearsBack() + "年" : "默认");
    }

    private String softenLocalAnswer(String answer) {
        String softened = firstNonBlank(answer, "我这里暂时没有查到足够资料，不过可以继续换个关键词再试。");
        softened = softened.replace("按当前筛选条件，", "我这里查到，");
        softened = softened.replace("按当前高保护 / 高风险筛选条件，", "我这里按高保护和高风险条件查了一下，");
        return softened;
    }

    private String normalizeAssistantAnswer(String answer) {
        String cleaned = answer == null ? "" : answer.trim();
        cleaned = cleaned.replaceFirst("^```(?:markdown|text)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        return firstNonBlank(cleaned, "我这里暂时没有组织出可靠回答，可以换个问法再试。");
    }

    private String truncateForPrompt(String value, int maxLength) {
        String normalized = normalize(value);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        if ("assistant".equals(normalized) || "system".equals(normalized) || "user".equals(normalized)) {
            return normalized;
        }
        return "user";
    }

    private String buildCacheKey(AssistantAiDtos.ChatRequest request) {
        String messageKey = normalize(request.message()).toLowerCase(Locale.ROOT);
        if (request.history() == null || request.history().isEmpty()) {
            return messageKey;
        }
        List<AssistantAiDtos.ConversationMessage> history = request.history().stream()
                .filter(item -> item != null && StringUtils.hasText(item.role()) && StringUtils.hasText(item.content()))
                .toList();
        String historyKey = history.stream()
                .skip(Math.max(0, history.size() - 2))
                .map(item -> item.role().trim().toLowerCase(Locale.ROOT) + ":" + normalize(item.content()).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining("|"));
        return messageKey + "||" + historyKey;
    }

    private static Map<String, List<String>> locationAliasLookup() {
        return Map.ofEntries(
                Map.entry("湛江", List.of("湛江", "湛江附近", "湛江周边", "湛江近海", "湛江沿岸", "湛江外海")),
                Map.entry("南海北部", List.of("南海北部", "南海北部海域", "南海北部近海", "南海北部近岸")),
                Map.entry("近岸样带", List.of("近岸样带", "近岸样带区", "近岸样带调查带", "近岸调查样带", "近岸带", "样带")),
                Map.entry("珠江口", List.of("珠江口", "伶仃洋", "珠江口近海", "珠江口海域")),
                Map.entry("珠江口外海", List.of("珠江口外海", "珠江口口外", "伶仃洋外海", "万山群岛", "担杆列岛")),
                Map.entry("北部湾", List.of("北部湾", "北部湾近海", "北部湾海域", "北部湾西岸", "广西近海", "防城港近海", "钦州湾")),
                Map.entry("雷州半岛", List.of("雷州半岛", "雷州半岛近海", "雷州半岛沿岸", "雷州湾")),
                Map.entry("东里海草床", List.of("东里海草床", "东里海草床样带", "湛江东里海草床", "东里样带")),
                Map.entry("红树林", List.of("红树林", "红树林生态系统")),
                Map.entry("海草床", List.of("海草床", "海草床生态系统")),
                Map.entry("珊瑚礁", List.of("珊瑚礁", "珊瑚礁生态系统"))
        );
    }

    private AssistantAiDtos.StructuredQuery extractStructuredQueryFast(String question) {
        String message = normalize(question);
        List<ObservationView> observationSamples = observationService
                .list(null, null, LocalDateTime.now().minusYears(5), null, 1, 100)
                .items();
        List<SpeciesView> speciesSamples = speciesService
                .listSpecies(null, 1, null, null, null, null, 1, 100)
                .items();

        Integer recentDays = extractNumber(message, DAYS_PATTERN);
        Integer yearsBack = extractNumber(message, YEARS_PATTERN);
        if (yearsBack == null) {
            yearsBack = extractChineseYears(message);
        }

        boolean includeTrend = containsAny(message, TREND_KEYWORDS);
        boolean riskOnly = containsAny(message, RISK_KEYWORDS);
        String protectionLevel = extractProtectionLevel(message);
        String iucnStatus = extractIucnStatus(message);
        String ecosystemKeyword = firstNonBlank(
                pickLongestMatch(message, observationSamples.stream().map(ObservationView::ecosystemName).toList()),
                pickLongestMatch(message, ECOSYSTEM_HINTS)
        );
        String locationKeyword = resolveLocationKeyword(message, observationSamples, speciesSamples);
        String speciesKeyword = pickLongestMatch(
                message,
                speciesSamples.stream()
                        .flatMap(item -> List.of(item.chineseName(), item.scientificName()).stream())
                        .toList()
        );
        String intent = inferIntent(message, speciesKeyword, ecosystemKeyword, includeTrend, riskOnly);

        return new AssistantAiDtos.StructuredQuery(
                intent,
                emptyToNull(locationKeyword),
                emptyToNull(ecosystemKeyword),
                emptyToNull(speciesKeyword),
                emptyToNull(protectionLevel),
                emptyToNull(iucnStatus),
                yearsBack,
                recentDays,
                includeTrend,
                riskOnly,
                inferLimit(message, intent)
        );
    }

    private AssistantAiDtos.StructuredQuery extractStructuredQueryFast(
            String question,
            List<AssistantAiDtos.ConversationMessage> history
    ) {
        return extractStructuredQueryFast(question, history, 0);
    }

    private AssistantAiDtos.StructuredQuery extractStructuredQueryFast(
            String question,
            List<AssistantAiDtos.ConversationMessage> history,
            int depth
    ) {
        AssistantAiDtos.StructuredQuery currentPlan = extractStructuredQueryFast(question);
        if (depth >= 3 || !shouldInheritContext(question, currentPlan)) {
            return currentPlan;
        }
        PreviousUserTurn previousTurn = findLatestPreviousUserTurn(history, question);
        if (previousTurn == null || !StringUtils.hasText(previousTurn.message())) {
            return currentPlan;
        }
        AssistantAiDtos.StructuredQuery previousPlan = extractStructuredQueryFast(
                previousTurn.message(),
                previousTurn.earlierHistory(),
                depth + 1
        );
        return mergeStructuredQuery(previousPlan, currentPlan, question);
    }

    private AssistantContext collectContext(AssistantAiDtos.StructuredQuery plan) {
        DashboardSummary dashboardSummary = reportService.dashboardSummary();
        if ("general_chat".equals(safe(plan.intent()))) {
            return new AssistantContext(
                    dashboardSummary,
                    List.of(),
                    List.of(),
                    loadGeneralChatSpecies(plan),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        List<EcosystemAnalyticsPoint> ecosystemAnalytics = reportService.ecosystemAnalytics().stream()
                .filter(item -> !StringUtils.hasText(plan.ecosystemKeyword())
                        || containsIgnoreCase(item.ecosystemName(), plan.ecosystemKeyword())
                        || containsIgnoreCase(item.ecosystemType(), plan.ecosystemKeyword()))
                .sorted(Comparator.comparingLong(EcosystemAnalyticsPoint::observationCount).reversed())
                .toList();

        LocalDateTime observedFrom = resolveObservedFrom(plan);
        int fetchLimit = Math.min(
                Math.max(resolveLimit(plan.limit(), 10) * 4, 24),
                Math.max(aiProperties.assistantObservationLimit(), 24)
        );
        PageResponse<ObservationView> observationPage = observationService.list(null, null, observedFrom, null, 1, fetchLimit);
        List<ObservationView> observationViews = observationPage.items().stream()
                .filter(item -> matchesBasicFilters(item, plan))
                .sorted(Comparator.comparing(ObservationView::observedAt).reversed())
                .toList();

        Map<Long, SpeciesDetailView> speciesCache = new LinkedHashMap<>();
        List<ObservationDetailView> observations = new ArrayList<>();
        if (needsObservationDetails(plan, observationViews)) {
            int detailLimit = Math.min(Math.max(resolveLimit(plan.limit(), 10) * 2, 12), 40);
            for (ObservationView view : observationViews.stream().limit(detailLimit).toList()) {
                observations.add(observationService.getDetail(view.id()));
            }
        }

        if (needsSpeciesFilter(plan)) {
            observations = observations.stream()
                    .filter(detail -> detail.speciesItems().stream()
                            .map(item -> speciesCache.computeIfAbsent(item.speciesId(), speciesService::getSpecies))
                            .anyMatch(detailView -> matchesSpeciesDetail(detailView, plan)))
                    .toList();
            Set<Long> matchedObservationIds = observations.stream()
                    .map(ObservationDetailView::id)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            observationViews = observationViews.stream()
                    .filter(item -> matchedObservationIds.contains(item.id()))
                    .toList();
        }

        LinkedHashMap<Long, SpeciesDetailView> speciesMap = new LinkedHashMap<>();
        for (ObservationDetailView detail : observations) {
            for (ObservationSpeciesView item : detail.speciesItems()) {
                SpeciesDetailView species = speciesCache.computeIfAbsent(item.speciesId(), speciesService::getSpecies);
                if (matchesSpeciesDetail(species, plan)) {
                    speciesMap.putIfAbsent(species.id(), species);
                }
            }
        }

        if (shouldLoadSpeciesArchive(plan, speciesMap.isEmpty())) {
            int speciesLimit = Math.min(
                    Math.max(resolveLimit(plan.limit(), aiProperties.assistantSpeciesLimit()) * 3, 20),
                    100
            );
            List<SpeciesDetailView> archiveSpecies = speciesService.listSpecies(
                            emptyToNull(plan.speciesKeyword()),
                            1,
                            emptyToNull(plan.protectionLevel()),
                            emptyToNull(plan.iucnStatus()),
                            emptyToNull(plan.locationKeyword()),
                            null,
                            1,
                            speciesLimit
                    )
                    .items()
                    .stream()
                    .map(item -> speciesService.getSpecies(item.id()))
                    .filter(detail -> matchesSpeciesDetail(detail, plan))
                    .toList();

            if (archiveSpecies.isEmpty()
                    && (plan.riskOnly()
                    || StringUtils.hasText(plan.protectionLevel())
                    || StringUtils.hasText(plan.iucnStatus()))) {
                archiveSpecies = speciesService.listSpecies(
                                emptyToNull(plan.speciesKeyword()),
                                1,
                                null,
                                null,
                                emptyToNull(plan.locationKeyword()),
                                null,
                                1,
                                speciesLimit
                        )
                        .items()
                        .stream()
                        .map(item -> speciesService.getSpecies(item.id()))
                        .filter(detail -> matchesSpeciesDetail(detail, plan))
                        .toList();
            }
            archiveSpecies.forEach(detail -> speciesMap.putIfAbsent(detail.id(), detail));
        }

        List<SpeciesDetailView> species = speciesMap.values().stream()
                .sorted(this::compareSpeciesPriority)
                .limit(resolveLimit(plan.limit(), aiProperties.assistantSpeciesLimit()))
                .toList();
        List<TrendPoint> trendPoints = shouldBuildTrend(plan, observationViews)
                ? (observations.isEmpty() ? aggregateViewTrend(observationViews) : aggregateDetailTrend(observations))
                : List.of();

        return new AssistantContext(
                dashboardSummary,
                observationViews,
                observations,
                species,
                trendPoints,
                aggregateObserverActivities(observationViews),
                ecosystemAnalytics
        );
    }

    private List<SpeciesDetailView> loadGeneralChatSpecies(AssistantAiDtos.StructuredQuery plan) {
        if (!StringUtils.hasText(plan.speciesKeyword())) {
            return List.of();
        }
        int speciesLimit = Math.min(Math.max(resolveLimit(plan.limit(), aiProperties.assistantSpeciesLimit()), 8), 20);
        return speciesService.listSpecies(
                        emptyToNull(plan.speciesKeyword()),
                        1,
                        null,
                        null,
                        null,
                        null,
                        1,
                        speciesLimit
                )
                .items()
                .stream()
                .map(item -> speciesService.getSpecies(item.id()))
                .filter(detail -> containsIgnoreCase(detail.chineseName(), plan.speciesKeyword())
                        || containsIgnoreCase(detail.scientificName(), plan.speciesKeyword())
                        || containsIgnoreCase(detail.classificationPath(), plan.speciesKeyword()))
                .sorted(this::compareSpeciesPriority)
                .limit(resolveLimit(plan.limit(), aiProperties.assistantSpeciesLimit()))
                .toList();
    }

    private AssistantAiDtos.ChatResponse buildLocalResponse(
            AssistantAiDtos.StructuredQuery plan,
            AssistantContext context
    ) {
        String answer = switch (safe(plan.intent())) {
            case "general_chat" -> buildGeneralChatAnswer(plan, context);
            case "map_scope" -> buildMapScopeAnswer(plan, context);
            case "observation_activity" -> buildObservationActivityAnswer(plan, context);
            case "ecosystem_trend" -> buildTrendAnswer(plan, context);
            case "observation_lookup" -> buildObservationAnswer(plan, context);
            case "species_profile" -> buildSpeciesProfileAnswer(plan, context);
            case "species_lookup" -> buildSpeciesAnswer(plan, context);
            default -> buildOverviewAnswer(plan, context);
        };
        return new AssistantAiDtos.ChatResponse(
                answer,
                plan,
                buildHighlights(plan, context),
                buildEvidence(plan, context),
                false
        );
    }

    private String buildGeneralChatAnswer(AssistantAiDtos.StructuredQuery plan, AssistantContext context) {
        if (!context.species().isEmpty()) {
            SpeciesDetailView species = context.species().get(0);
            return "这个问题更偏日常或通用问答。系统里和它对应的物种档案是 "
                    + displaySpeciesName(species)
                    + "，可参考信息包括："
                    + "保护等级 " + firstNonBlank(species.protectionLevel(), "未标注")
                    + "，IUCN 状态 " + firstNonBlank(species.iucnStatus(), "未标注")
                    + "，分布 " + firstNonBlank(species.geoRangeText(), species.distribution(), "未填写")
                    + "。如果 DeepSeek 服务可用，我会结合这些资料和通用知识直接回答你的原问题。";
        }
        return "这是一个通用问题，我会优先按自然问答来回答，并把 RAG 检索到的资料作为参考；当前如果模型服务不可用，就只能先给出有限的系统资料。";
    }

    private AssistantAiDtos.ChatResponse buildLightweightResponse(
            AssistantAiDtos.StructuredQuery plan,
            AssistantAiDtos.ChatRequest request
    ) {
        String answer = switch (safe(plan.intent())) {
            case "clarify" -> buildClarificationAnswer(request);
            case "capability_help" -> buildCapabilityAnswer();
            default -> "我已经收到你的问题了，但当前还缺少足够的查询线索。你可以补充地点、时间或物种名，我会继续往下查。";
        };
        return new AssistantAiDtos.ChatResponse(
                answer,
                plan,
                buildLightweightHighlights(plan),
                buildLightweightEvidence(plan, request),
                false
        );
    }

    private String buildObservationActivityAnswer(AssistantAiDtos.StructuredQuery plan, AssistantContext context) {
        if (context.observerActivities().isEmpty()) {
            return buildNoDataAnswer(plan, context.dashboardSummary());
        }
        ObservationActivity top = context.observerActivities().get(0);
        StringBuilder answer = new StringBuilder();
        answer.append("按当前筛选条件，最活跃的观测人员是 ")
                .append(top.observerName())
                .append("，共记录 ")
                .append(top.count())
                .append(" 次观测。");
        if (context.observerActivities().size() > 1) {
            String ranking = context.observerActivities().stream()
                    .limit(3)
                    .map(item -> item.observerName() + "（" + item.count() + " 次）")
                    .collect(Collectors.joining("、"));
            answer.append(" 当前前三位依次为 ").append(ranking).append("。");
        }
        if (StringUtils.hasText(plan.locationKeyword())) {
            answer.append(" 地点过滤为 ").append(plan.locationKeyword()).append("。");
        }
        return answer.toString();
    }

    private String buildObservationAnswer(AssistantAiDtos.StructuredQuery plan, AssistantContext context) {
        if (context.observationViews().isEmpty()) {
            if (!context.species().isEmpty()) {
                return "按当前筛选条件，暂未匹配到直接相关的观测记录，但系统物种档案中仍有 "
                        + context.species().size()
                        + " 个相关物种可供参考，包括 "
                        + joinSpeciesNames(context.species(), 5)
                        + "。";
            }
            return buildNoDataAnswer(plan, context.dashboardSummary());
        }

        LocalDateTime newest = context.observationViews().stream()
                .map(ObservationView::observedAt)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
        LocalDateTime oldest = context.observationViews().stream()
                .map(ObservationView::observedAt)
                .min(LocalDateTime::compareTo)
                .orElse(newest);

        StringBuilder answer = new StringBuilder();
        answer.append("按当前筛选条件，共匹配到 ")
                .append(context.observationViews().size())
                .append(" 条观测记录，时间范围从 ")
                .append(formatDate(oldest))
                .append(" 到 ")
                .append(formatDate(newest))
                .append("。");
        if (StringUtils.hasText(plan.locationKeyword())) {
            answer.append(" 地点范围已过滤为 ").append(plan.locationKeyword()).append("。");
        }
        if (StringUtils.hasText(plan.ecosystemKeyword())) {
            answer.append(" 生态系统聚焦于 ").append(plan.ecosystemKeyword()).append("。");
        }
        if (!context.species().isEmpty()) {
            answer.append(" 关联物种包括 ").append(joinSpeciesNames(context.species(), 6)).append("。");
        }
        answer.append(" 主要观测地点有 ").append(joinObservationLocations(context.observationViews(), 4)).append("。");
        if (!context.observerActivities().isEmpty()) {
            ObservationActivity top = context.observerActivities().get(0);
            answer.append(" 其中最活跃的观测人员是 ")
                    .append(top.observerName())
                    .append("，共 ")
                    .append(top.count())
                    .append(" 次。");
        }
        if (!context.trendPoints().isEmpty()) {
            answer.append(" ").append(summarizeTrendSentence(context.trendPoints()));
        }
        return answer.toString();
    }

    private String buildSpeciesAnswer(AssistantAiDtos.StructuredQuery plan, AssistantContext context) {
        if (context.species().isEmpty()) {
            return buildNoDataAnswer(plan, context.dashboardSummary());
        }

        StringBuilder answer = new StringBuilder();
        if (plan.riskOnly() || StringUtils.hasText(plan.protectionLevel()) || StringUtils.hasText(plan.iucnStatus())) {
            answer.append("按当前高保护 / 高风险筛选条件，");
        } else {
            answer.append("按当前筛选条件，");
        }
        answer.append("系统中共匹配到 ")
                .append(context.species().size())
                .append(" 个相关物种，包括 ")
                .append(joinSpeciesNames(context.species(), 6))
                .append("。");
        answer.append(" 从分布特点看，").append(summarizeDistributionFeatures(context.species())).append("。");
        if (!context.observationViews().isEmpty()) {
            answer.append(" 同时关联到 ")
                    .append(context.observationViews().size())
                    .append(" 条观测记录。");
        }
        return answer.toString();
    }

    private String buildSpeciesProfileAnswer(AssistantAiDtos.StructuredQuery plan, AssistantContext context) {
        if (context.species().isEmpty()) {
            return buildNoDataAnswer(plan, context.dashboardSummary());
        }

        SpeciesDetailView species = context.species().get(0);
        String displayName = displaySpeciesName(species);
        StringBuilder answer = new StringBuilder();
        answer.append(displayName).append("是");
        String description = firstNonBlank(species.description(), species.classificationPath(), "系统物种库中的一个海洋生物物种");
        answer.append(trimTrailingPunctuation(description)).append("。");

        List<String> facts = new ArrayList<>();
        if (StringUtils.hasText(species.classificationPath())) {
            facts.add("分类上属于 " + species.classificationPath());
        }
        if (StringUtils.hasText(species.protectionLevel()) || StringUtils.hasText(species.iucnStatus())) {
            facts.add("保护信息为 " + firstNonBlank(species.protectionLevel(), "未标注保护等级")
                    + "，IUCN 状态 " + firstNonBlank(species.iucnStatus(), "未标注"));
        }
        if (StringUtils.hasText(species.geoRangeText()) || StringUtils.hasText(species.distribution())) {
            facts.add("分布范围主要在 " + firstNonBlank(species.geoRangeText(), species.distribution()));
        }
        if (StringUtils.hasText(species.habitat())) {
            facts.add("常见栖息环境是 " + trimTrailingPunctuation(species.habitat()));
        }
        if (!facts.isEmpty()) {
            answer.append(" ").append(String.join("；", facts)).append("。");
        }

        if (StringUtils.hasText(species.morphology())) {
            answer.append(" 形态特征方面，").append(trimTrailingPunctuation(species.morphology())).append("。");
        }
        if (StringUtils.hasText(species.habit())) {
            answer.append(" 生活习性方面，").append(trimTrailingPunctuation(species.habit())).append("。");
        }
        if (!context.observationViews().isEmpty()) {
            answer.append(" 在当前系统数据里，它还关联到 ")
                    .append(context.observationViews().size())
                    .append(" 条观测记录，可继续追问“最近在哪里观测到”或“分布范围”。");
        }
        return answer.toString();
    }

    private String buildTrendAnswer(AssistantAiDtos.StructuredQuery plan, AssistantContext context) {
        if (context.trendPoints().isEmpty() && context.ecosystemAnalytics().isEmpty()) {
            return buildNoDataAnswer(plan, context.dashboardSummary());
        }

        String scope = firstNonBlank(plan.ecosystemKeyword(), plan.locationKeyword(), "当前筛选范围");
        StringBuilder answer = new StringBuilder();
        answer.append(scope).append(" 的趋势分析显示，");

        if (!context.trendPoints().isEmpty()) {
            answer.append(summarizeTrendSentence(context.trendPoints()));
        } else {
            answer.append("当前缺少足够的月度变化数据。");
        }

        if (!context.ecosystemAnalytics().isEmpty()) {
            EcosystemAnalyticsPoint top = context.ecosystemAnalytics().get(0);
            answer.append(" 当前累计观测 ")
                    .append(top.observationCount())
                    .append(" 次，发现物种 ")
                    .append(top.speciesCount())
                    .append(" 种。");
        }
        if (!context.species().isEmpty()) {
            answer.append(" 代表性物种包括 ").append(joinSpeciesNames(context.species(), 5)).append("。");
        }
        return answer.toString();
    }

    private String buildMapScopeAnswer(AssistantAiDtos.StructuredQuery plan, AssistantContext context) {
        if (context.observationViews().isEmpty() && context.ecosystemAnalytics().isEmpty()) {
            return buildNoDataAnswer(plan, context.dashboardSummary());
        }

        List<String> locations = context.observationViews().stream()
                .map(item -> firstNonBlank(item.locationName(), item.ecosystemName()))
                .filter(StringUtils::hasText)
                .distinct()
                .limit(6)
                .toList();
        List<String> ecosystems = context.ecosystemAnalytics().stream()
                .map(item -> firstNonBlank(item.ecosystemName(), item.ecosystemType()))
                .filter(StringUtils::hasText)
                .distinct()
                .limit(4)
                .toList();

        StringBuilder answer = new StringBuilder();
        if (StringUtils.hasText(plan.locationKeyword())) {
            answer.append("这张地图当前已经按“")
                    .append(plan.locationKeyword())
                    .append("”做了范围聚焦，");
        } else {
            answer.append("这张地图展示的是系统里已经录入的观测点和生态系统数据，不是单一一块固定海域，");
        }
        if (!locations.isEmpty()) {
            answer.append("目前主要覆盖 ")
                    .append(String.join("、", locations))
                    .append(" 等区域");
            if (context.observationViews().size() > locations.size()) {
                answer.append("，共 ").append(context.observationViews().size()).append(" 个点位");
            }
            answer.append("。");
        }
        if (!ecosystems.isEmpty()) {
            answer.append(" 涉及的生态系统类型主要有 ")
                    .append(String.join("、", ecosystems))
                    .append("。");
        }
        if (!context.species().isEmpty()) {
            answer.append(" 这些区域里的代表性物种包括 ")
                    .append(joinSpeciesNames(context.species(), 5))
                    .append("。");
        }
        answer.append(" 如果你想继续缩小范围，可以直接追问“只看湛江近海”“只看南海北部”或“只看近30天点位”。");
        return answer.toString();
    }

    private String buildOverviewAnswer(AssistantAiDtos.StructuredQuery plan, AssistantContext context) {
        if (context.species().isEmpty() && context.observationViews().isEmpty()) {
            return buildNoDataAnswer(plan, context.dashboardSummary());
        }

        String focus = firstNonBlank(plan.locationKeyword(), plan.ecosystemKeyword(), plan.speciesKeyword());
        StringBuilder answer = new StringBuilder();
        if (StringUtils.hasText(focus)) {
            answer.append("围绕“")
                    .append(focus)
                    .append("”这部分数据来看，");
        } else {
            answer.append("先给你一个和当前问题最相关的概览：");
        }
        if (!context.species().isEmpty()) {
            answer.append(" 代表性物种包括 ")
                    .append(joinSpeciesNames(context.species(), 5))
                    .append("。");
        }
        if (!context.observationViews().isEmpty()) {
            answer.append(" 相关观测主要出现在 ")
                    .append(joinObservationLocations(context.observationViews(), 4))
                    .append("。");
        }
        answer.append(" 系统当前累计保存物种档案 ")
                .append(context.dashboardSummary().totalSpecies())
                .append(" 条、观测记录 ")
                .append(context.dashboardSummary().totalObservations())
                .append(" 条、生态系统 ")
                .append(context.dashboardSummary().totalEcosystems())
                .append(" 个。");
        return answer.toString();
    }

    private List<String> buildHighlights(AssistantAiDtos.StructuredQuery plan, AssistantContext context) {
        List<String> highlights = new ArrayList<>();
        if ("general_chat".equals(safe(plan.intent()))) {
            if (!context.species().isEmpty()) {
                highlights.add("通用问答模式：已找到 " + context.species().size() + " 个相关物种档案");
            } else {
                highlights.add("通用问答模式：RAG 证据作为可选参考");
            }
            return highlights;
        }
        if (!context.observationViews().isEmpty()) {
            highlights.add("匹配到 " + context.observationViews().size() + " 条观测记录");
        }
        if (!"observation_activity".equals(plan.intent()) && !context.species().isEmpty()) {
            highlights.add("匹配到 " + context.species().size() + " 个相关物种");
        }
        if (!context.ecosystemAnalytics().isEmpty()) {
            EcosystemAnalyticsPoint top = context.ecosystemAnalytics().get(0);
            highlights.add(top.ecosystemName() + " 观测次数 " + top.observationCount() + " 次");
        }
        if (!context.observerActivities().isEmpty()) {
            ObservationActivity top = context.observerActivities().get(0);
            highlights.add(top.observerName() + " 是当前条件下最活跃的观测人员");
        }
        if (plan.riskOnly()) {
            highlights.add("已启用高保护 / 高风险筛选");
        }
        if (StringUtils.hasText(plan.locationKeyword())) {
            highlights.add("地点过滤：" + plan.locationKeyword());
        }
        if (highlights.isEmpty()) {
            highlights.add("当前条件下没有直接命中完整数据，建议缩小时间或地点范围再试");
        }
        return highlights.stream().limit(4).toList();
    }

    private List<String> buildLightweightHighlights(AssistantAiDtos.StructuredQuery plan) {
        return switch (safe(plan.intent())) {
            case "clarify" -> List.of(
                    "当前问题太短，系统还拿不到明确查询目标",
                    "补充地点、时间或对象后，回答会更具体",
                    "可以直接追问地图范围、观测记录、物种分布或趋势"
            );
            case "capability_help" -> List.of(
                    "支持物种分布、观测记录、生态系统和趋势总结",
                    "支持模糊地名识别，例如湛江周边、南海北部、近岸样带",
                    "重复问题会优先命中缓存，返回更快"
            );
            default -> List.of("当前已切换到轻量回答模式");
        };
    }

    private List<AssistantAiDtos.EvidenceItem> buildEvidence(AssistantAiDtos.StructuredQuery plan, AssistantContext context) {
        List<AssistantAiDtos.EvidenceItem> evidence = new ArrayList<>();
        if ("general_chat".equals(safe(plan.intent()))) {
            context.species().stream().limit(3).forEach(item -> evidence.add(new AssistantAiDtos.EvidenceItem(
                    "species",
                    firstNonBlank(item.chineseName(), item.scientificName(), "物种档案"),
                    "保护等级：" + firstNonBlank(item.protectionLevel(), "未标注")
                            + "；IUCN：" + firstNonBlank(item.iucnStatus(), "未标注")
                            + "；分布：" + firstNonBlank(item.geoRangeText(), item.distribution(), "未填写")
            )));
            if (evidence.isEmpty()) {
                evidence.add(new AssistantAiDtos.EvidenceItem(
                        "mode",
                        "通用问答",
                        "该问题未触发系统统计意图，将优先由通用模型回答，并把 RAG 检索结果作为参考。"
                ));
            }
            return evidence;
        }
        evidence.add(new AssistantAiDtos.EvidenceItem(
                "dashboard",
                "系统概况",
                "物种 " + context.dashboardSummary().totalSpecies()
                        + " 条，观测 " + context.dashboardSummary().totalObservations()
                        + " 条，生态系统 " + context.dashboardSummary().totalEcosystems() + " 个"
        ));

        if (!"observation_activity".equals(plan.intent())) {
            context.species().stream().limit(3).forEach(item -> evidence.add(new AssistantAiDtos.EvidenceItem(
                    "species",
                    firstNonBlank(item.chineseName(), item.scientificName(), "物种档案"),
                    "保护等级：" + firstNonBlank(item.protectionLevel(), "未标注")
                            + "；IUCN：" + firstNonBlank(item.iucnStatus(), "未标注")
                            + "；分布：" + firstNonBlank(item.geoRangeText(), item.distribution(), "未填写")
            )));
        }

        context.observationViews().stream().limit(3).forEach(item -> evidence.add(new AssistantAiDtos.EvidenceItem(
                "observation",
                firstNonBlank(item.locationName(), item.ecosystemName(), "观测记录"),
                formatDate(item.observedAt()) + "；观测人员：" + firstNonBlank(item.observerName(), "未标注")
        )));

        context.ecosystemAnalytics().stream().limit(2).forEach(item -> evidence.add(new AssistantAiDtos.EvidenceItem(
                "ecosystem",
                firstNonBlank(item.ecosystemName(), item.ecosystemType(), "生态系统"),
                "观测次数 " + item.observationCount() + " 次；发现物种 " + item.speciesCount() + " 种"
        )));

        if (StringUtils.hasText(plan.speciesKeyword()) && context.species().isEmpty()) {
            evidence.add(new AssistantAiDtos.EvidenceItem(
                    "hint",
                    "检索提示",
                    "当前物种关键词没有命中物种档案，可尝试减少修饰词或只保留中文名 / 学名再查询"
            ));
        }
        return evidence.stream().limit(6).toList();
    }

    private List<AssistantAiDtos.EvidenceItem> buildLightweightEvidence(
            AssistantAiDtos.StructuredQuery plan,
            AssistantAiDtos.ChatRequest request
    ) {
        List<AssistantAiDtos.EvidenceItem> evidence = new ArrayList<>();
        if ("clarify".equals(safe(plan.intent()))) {
            String previousQuestion = findLatestUserQuestion(request.history(), request.message());
            evidence.add(new AssistantAiDtos.EvidenceItem(
                    "hint",
                    "提问建议",
                    StringUtils.hasText(previousQuestion)
                            ? "如果你是在继续上一题“" + truncateForReply(previousQuestion, 18) + "”，可以直接补一句“只看范围”“展开点位”或“按近30天再看”。"
                            : "可以补充地点、时间、生态系统或物种名，例如“近30天湛江附近有哪些观测记录”。"
            ));
            return evidence;
        }
        evidence.add(new AssistantAiDtos.EvidenceItem(
                "capability",
                "支持的提问方式",
                "可直接提问物种分布、观测地点、生态系统趋势、保护等级、观测活跃度等问题。"
        ));
        evidence.add(new AssistantAiDtos.EvidenceItem(
                "example",
                "示例问题",
                "例如：最近三年在湛江附近观测到的濒危物种有哪些？"
        ));
        return evidence;
    }

    private boolean matchesBasicFilters(ObservationView item, AssistantAiDtos.StructuredQuery plan) {
        if (StringUtils.hasText(plan.locationKeyword())) {
            boolean matchedLocation = matchesLocationText(item.locationName(), plan.locationKeyword())
                    || matchesLocationText(item.note(), plan.locationKeyword())
                    || matchesLocationText(item.ecosystemName(), plan.locationKeyword());
            if (!matchedLocation) {
                return false;
            }
        }
        if (StringUtils.hasText(plan.ecosystemKeyword())
                && !containsIgnoreCase(item.ecosystemName(), plan.ecosystemKeyword())) {
            return false;
        }
        return true;
    }

    private boolean matchesSpeciesDetail(SpeciesDetailView detail, AssistantAiDtos.StructuredQuery plan) {
        if (StringUtils.hasText(plan.speciesKeyword())) {
            boolean matchedKeyword = containsIgnoreCase(detail.chineseName(), plan.speciesKeyword())
                    || containsIgnoreCase(detail.scientificName(), plan.speciesKeyword())
                    || containsIgnoreCase(detail.classificationPath(), plan.speciesKeyword());
            if (!matchedKeyword) {
                return false;
            }
        }
        if (StringUtils.hasText(plan.locationKeyword())) {
            boolean matchedLocation = matchesLocationText(detail.geoRangeText(), plan.locationKeyword())
                    || matchesLocationText(detail.distribution(), plan.locationKeyword())
                    || matchesLocationText(detail.habitat(), plan.locationKeyword());
            if (!matchedLocation) {
                return false;
            }
        }
        if (StringUtils.hasText(plan.iucnStatus())
                && !containsIgnoreCase(detail.iucnStatus(), plan.iucnStatus())) {
            return false;
        }
        if (StringUtils.hasText(plan.protectionLevel())
                && !containsIgnoreCase(detail.protectionLevel(), plan.protectionLevel())) {
            return false;
        }
        if (plan.riskOnly()) {
            return isRiskSpecies(detail);
        }
        return true;
    }

    private boolean isRiskSpecies(SpeciesDetailView detail) {
        String status = normalize(detail.iucnStatus()).toUpperCase(Locale.ROOT);
        boolean statusRisk = RISK_STATUSES.contains(status)
                || containsAny(detail.iucnStatus(), List.of("极危", "濒危", "易危"));
        boolean protectionRisk = containsAny(
                detail.protectionLevel(),
                List.of("一级", "二级", "重点保护", "国家一级", "国家二级")
        );
        return statusRisk || protectionRisk;
    }

    private boolean shouldLoadSpeciesArchive(AssistantAiDtos.StructuredQuery plan, boolean speciesEmpty) {
        return "species_lookup".equals(plan.intent())
                || ("overview".equals(plan.intent()) && speciesEmpty)
                || StringUtils.hasText(plan.speciesKeyword())
                || StringUtils.hasText(plan.locationKeyword())
                || StringUtils.hasText(plan.protectionLevel())
                || StringUtils.hasText(plan.iucnStatus())
                || plan.riskOnly();
    }

    private boolean needsSpeciesFilter(AssistantAiDtos.StructuredQuery plan) {
        return StringUtils.hasText(plan.speciesKeyword())
                || StringUtils.hasText(plan.protectionLevel())
                || StringUtils.hasText(plan.iucnStatus())
                || plan.riskOnly();
    }

    private boolean needsObservationDetails(AssistantAiDtos.StructuredQuery plan, List<ObservationView> observationViews) {
        if (observationViews.isEmpty()) {
            return false;
        }
        return "observation_lookup".equals(plan.intent())
                || needsSpeciesFilter(plan)
                || shouldBuildTrend(plan, observationViews);
    }

    private boolean shouldBuildTrend(AssistantAiDtos.StructuredQuery plan, List<ObservationView> observationViews) {
        return plan.includeTrend()
                || "ecosystem_trend".equals(plan.intent())
                || (!observationViews.isEmpty() && StringUtils.hasText(plan.ecosystemKeyword()));
    }

    private LocalDateTime resolveObservedFrom(AssistantAiDtos.StructuredQuery plan) {
        if (plan.yearsBack() != null && plan.yearsBack() > 0) {
            return LocalDateTime.now().minusYears(plan.yearsBack());
        }
        if (plan.recentDays() != null && plan.recentDays() > 0) {
            return LocalDateTime.now().minusDays(plan.recentDays());
        }
        return LocalDateTime.now().minusDays(365);
    }

    private List<TrendPoint> aggregateDetailTrend(List<ObservationDetailView> observations) {
        Map<String, Set<Long>> speciesByMonth = new LinkedHashMap<>();
        Map<String, Integer> observationCountByMonth = new LinkedHashMap<>();
        for (ObservationDetailView observation : observations) {
            String month = observation.observedAt().format(MONTH_FORMATTER);
            observationCountByMonth.merge(month, 1, Integer::sum);
            speciesByMonth.computeIfAbsent(month, key -> new LinkedHashSet<>());
            for (ObservationSpeciesView item : observation.speciesItems()) {
                speciesByMonth.get(month).add(item.speciesId());
            }
        }
        return observationCountByMonth.keySet().stream()
                .sorted()
                .map(month -> new TrendPoint(
                        month,
                        observationCountByMonth.getOrDefault(month, 0),
                        speciesByMonth.getOrDefault(month, Set.of()).size()
                ))
                .toList();
    }

    private List<TrendPoint> aggregateViewTrend(List<ObservationView> observations) {
        Map<String, Integer> observationCountByMonth = new LinkedHashMap<>();
        for (ObservationView observation : observations) {
            String month = observation.observedAt().format(MONTH_FORMATTER);
            observationCountByMonth.merge(month, 1, Integer::sum);
        }
        return observationCountByMonth.keySet().stream()
                .sorted()
                .map(month -> new TrendPoint(month, observationCountByMonth.getOrDefault(month, 0), 0))
                .toList();
    }

    private List<ObservationActivity> aggregateObserverActivities(List<ObservationView> observationViews) {
        Map<String, Integer> countByObserver = new LinkedHashMap<>();
        for (ObservationView observation : observationViews) {
            String observerName = firstNonBlank(observation.observerName(), "未标注");
            countByObserver.merge(observerName, 1, Integer::sum);
        }
        return countByObserver.entrySet().stream()
                .map(entry -> new ObservationActivity(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(ObservationActivity::count).reversed())
                .toList();
    }

    private int compareSpeciesPriority(SpeciesDetailView left, SpeciesDetailView right) {
        int riskCompare = Integer.compare(riskScore(right), riskScore(left));
        if (riskCompare != 0) {
            return riskCompare;
        }
        return firstNonBlank(left.chineseName(), left.scientificName(), "")
                .compareToIgnoreCase(firstNonBlank(right.chineseName(), right.scientificName(), ""));
    }

    private int riskScore(SpeciesDetailView species) {
        String status = normalize(species.iucnStatus()).toUpperCase(Locale.ROOT);
        if ("CR".equals(status) || containsAny(species.iucnStatus(), List.of("极危"))) {
            return 5;
        }
        if ("EN".equals(status) || containsAny(species.iucnStatus(), List.of("濒危"))) {
            return 4;
        }
        if ("VU".equals(status) || containsAny(species.iucnStatus(), List.of("易危"))) {
            return 3;
        }
        if (containsAny(species.protectionLevel(), List.of("国家一级", "一级", "重点保护"))) {
            return 2;
        }
        if (containsAny(species.protectionLevel(), List.of("国家二级", "二级"))) {
            return 1;
        }
        return 0;
    }

    private int inferLimit(String message, String intent) {
        if (containsAny(message, List.of("全部", "所有"))) {
            return 20;
        }
        if (containsAny(message, List.of("列出", "哪些", "有哪些"))) {
            return "observation_lookup".equals(intent) ? 10 : 8;
        }
        if ("clarify".equals(intent) || "capability_help".equals(intent)) {
            return 4;
        }
        return "overview".equals(intent) || "general_chat".equals(intent) ? 6 : 8;
    }

    private String inferIntent(String message, String speciesKeyword, String ecosystemKeyword, boolean includeTrend, boolean riskOnly) {
        if (isClarifyOnlyMessage(message)) {
            return "clarify";
        }
        if (containsAny(message, HELP_KEYWORDS)) {
            return "capability_help";
        }
        if (isMapScopeQuestion(message)) {
            return "map_scope";
        }
        if (containsAny(message, ACTIVITY_KEYWORDS)) {
            return "observation_activity";
        }
        if (includeTrend || StringUtils.hasText(ecosystemKeyword)) {
            if (containsAny(message, List.of("生态系统", "生态", "趋势", "变化", "红树林", "珊瑚礁", "海草床"))) {
                return "ecosystem_trend";
            }
        }
        if (isObservationLookupQuestion(message, speciesKeyword, ecosystemKeyword)) {
            return "observation_lookup";
        }
        if (isSpeciesProfileQuestion(message, speciesKeyword)) {
            return "species_profile";
        }
        if (isCasualGeneralQuestion(message) && !hasExplicitSystemDataSignal(message)) {
            return "general_chat";
        }
        if (riskOnly || isSystemDataQuestion(message, speciesKeyword, ecosystemKeyword)) {
            return "species_lookup";
        }
        return "general_chat";
    }

    private boolean isObservationLookupQuestion(String message, String speciesKeyword, String ecosystemKeyword) {
        if (containsAny(message, List.of("观测到", "观测记录", "观测", "点位"))) {
            return true;
        }
        boolean hasDomainAnchor = hasDomainAnchor(message, speciesKeyword, ecosystemKeyword);
        if (containsAny(message, List.of("记录", "出现", "发现", "看到")) && hasDomainAnchor) {
            return true;
        }
        return containsAny(message, List.of("最近", "近30天", "近三年", "过去"))
                && hasDomainAnchor
                && hasExplicitSystemDataSignal(message);
    }

    private boolean isCasualGeneralQuestion(String message) {
        return containsAny(message, CASUAL_GENERAL_KEYWORDS);
    }

    private boolean hasExplicitSystemDataSignal(String message) {
        return containsAny(message, EXPLICIT_SYSTEM_DATA_KEYWORDS);
    }

    private boolean isSystemDataQuestion(String message, String speciesKeyword, String ecosystemKeyword) {
        if (hasExplicitSystemDataSignal(message)) {
            return true;
        }
        return hasDomainAnchor(message, speciesKeyword, ecosystemKeyword) && containsAny(message, SYSTEM_DATA_KEYWORDS);
    }

    private boolean hasDomainAnchor(String message, String speciesKeyword, String ecosystemKeyword) {
        return StringUtils.hasText(speciesKeyword)
                || StringUtils.hasText(ecosystemKeyword)
                || containsAny(message, List.of("物种", "海洋生物", "生态系统", "保护", "分布"));
    }

    private boolean isSpeciesProfileQuestion(String message, String speciesKeyword) {
        if (!StringUtils.hasText(speciesKeyword)) {
            return false;
        }
        String normalizedMessage = normalize(message);
        if (containsAny(normalizedMessage, SPECIES_OBSERVATION_KEYWORDS)
                || containsAny(normalizedMessage, List.of("分布", "哪里", "在哪", "地点", "趋势", "变化"))) {
            return false;
        }
        if (containsAny(normalizedMessage, SPECIES_PROFILE_KEYWORDS)) {
            return true;
        }
        String compactMessage = normalizedMessage.replaceAll("[\\s?？。！!，,、]+", "");
        String compactSpecies = speciesKeyword.replaceAll("[\\s?？。！!，,、]+", "");
        return compactMessage.equalsIgnoreCase(compactSpecies);
    }

    private boolean isClarifyOnlyMessage(String message) {
        String compact = normalize(message).replaceAll("\\s+", "");
        if (!StringUtils.hasText(compact)) {
            return true;
        }
        if (CLARIFY_ONLY_MESSAGES.contains(compact)) {
            return true;
        }
        return compact.length() <= 2
                && compact.chars().allMatch(ch -> !Character.isLetterOrDigit(ch) || ch == '?' || ch == '？');
    }

    private boolean isMapScopeQuestion(String message) {
        return containsAny(message, MAP_SCOPE_KEYWORDS)
                || (containsAny(message, List.of("地图", "点位"))
                && containsAny(message, List.of("范围", "覆盖", "涵盖", "区域", "海域", "哪里", "地方")));
    }

    private boolean shouldInheritContext(String message, AssistantAiDtos.StructuredQuery currentPlan) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String intent = safe(currentPlan.intent());
        if ("capability_help".equals(intent) || "clarify".equals(intent)) {
            return false;
        }
        if (isFilterOnlyFollowUp(message, currentPlan)) {
            return true;
        }
        return "overview".equals(intent)
                && (StringUtils.hasText(currentPlan.locationKeyword())
                || StringUtils.hasText(currentPlan.ecosystemKeyword())
                || StringUtils.hasText(currentPlan.speciesKeyword())
                || currentPlan.recentDays() != null
                || currentPlan.yearsBack() != null
                || StringUtils.hasText(currentPlan.protectionLevel())
                || StringUtils.hasText(currentPlan.iucnStatus())
                || currentPlan.riskOnly());
    }

    private boolean isFilterOnlyFollowUp(String message, AssistantAiDtos.StructuredQuery currentPlan) {
        String normalized = normalize(message);
        if (!StringUtils.hasText(normalized) || normalized.length() > 18) {
            return false;
        }
        if (!containsAny(normalized, FOLLOW_UP_HINTS)
                && !StringUtils.hasText(currentPlan.locationKeyword())
                && currentPlan.recentDays() == null
                && currentPlan.yearsBack() == null
                && !StringUtils.hasText(currentPlan.protectionLevel())
                && !StringUtils.hasText(currentPlan.iucnStatus())
                && !currentPlan.riskOnly()) {
            return false;
        }
        return !containsAny(normalized, HELP_KEYWORDS)
                && !isMapScopeQuestion(normalized)
                && !containsAny(normalized, ACTIVITY_KEYWORDS)
                && !containsAny(normalized, TREND_KEYWORDS)
                && !containsAny(normalized, List.of("物种", "观测记录", "生态系统", "分布", "趋势", "保护等级", "濒危"));
    }

    private AssistantAiDtos.StructuredQuery mergeStructuredQuery(
            AssistantAiDtos.StructuredQuery previousPlan,
            AssistantAiDtos.StructuredQuery currentPlan,
            String currentMessage
    ) {
        boolean filterOnlyFollowUp = isFilterOnlyFollowUp(currentMessage, currentPlan);
        String mergedIntent = safe(currentPlan.intent());
        if ((filterOnlyFollowUp || "overview".equals(mergedIntent))
                && StringUtils.hasText(previousPlan.intent())
                && !"overview".equals(previousPlan.intent())
                && !"clarify".equals(previousPlan.intent())
                && !"capability_help".equals(previousPlan.intent())) {
            mergedIntent = previousPlan.intent();
        }

        Integer mergedRecentDays;
        Integer mergedYearsBack;
        if (currentPlan.recentDays() != null) {
            mergedRecentDays = currentPlan.recentDays();
            mergedYearsBack = null;
        } else if (currentPlan.yearsBack() != null) {
            mergedYearsBack = currentPlan.yearsBack();
            mergedRecentDays = null;
        } else {
            mergedRecentDays = previousPlan.recentDays();
            mergedYearsBack = previousPlan.yearsBack();
        }

        return new AssistantAiDtos.StructuredQuery(
                mergedIntent,
                firstNonBlank(currentPlan.locationKeyword(), previousPlan.locationKeyword()),
                firstNonBlank(currentPlan.ecosystemKeyword(), previousPlan.ecosystemKeyword()),
                firstNonBlank(currentPlan.speciesKeyword(), previousPlan.speciesKeyword()),
                firstNonBlank(currentPlan.protectionLevel(), previousPlan.protectionLevel()),
                firstNonBlank(currentPlan.iucnStatus(), previousPlan.iucnStatus()),
                mergedYearsBack,
                mergedRecentDays,
                currentPlan.includeTrend() || previousPlan.includeTrend(),
                currentPlan.riskOnly() || previousPlan.riskOnly(),
                currentPlan.limit() != null ? currentPlan.limit() : previousPlan.limit()
        );
    }

    private Integer extractNumber(String source, Pattern pattern) {
        Matcher matcher = pattern.matcher(source);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private Integer extractChineseYears(String source) {
        for (Map.Entry<String, Integer> entry : CHINESE_YEAR_WORDS.entrySet()) {
            if (source.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String extractProtectionLevel(String source) {
        if (containsAny(source, List.of("国家一级", "一级保护", "一级重点"))) {
            return "一级";
        }
        if (containsAny(source, List.of("国家二级", "二级保护", "二级重点"))) {
            return "二级";
        }
        return null;
    }

    private String extractIucnStatus(String source) {
        if (containsAny(source, List.of("CR", "极危"))) {
            return "CR";
        }
        if (containsAny(source, List.of("EN", "濒危"))) {
            return "EN";
        }
        if (containsAny(source, List.of("VU", "易危"))) {
            return "VU";
        }
        return null;
    }

    private String resolveLocationKeyword(String message, List<ObservationView> observationSamples, List<SpeciesView> speciesSamples) {
        for (Map.Entry<String, List<String>> entry : locationAliasLookup().entrySet()) {
            if (containsIgnoreCase(message, entry.getKey())
                    || entry.getValue().stream().anyMatch(alias -> containsIgnoreCase(message, alias))) {
                return entry.getKey();
            }
        }
        String bySuffix = simplifyLocationToken(extractLocationBySuffix(message));
        if (StringUtils.hasText(bySuffix)) {
            return bySuffix;
        }
        Set<String> lexicon = buildLocationLexicon(observationSamples, speciesSamples);
        return simplifyLocationToken(pickLongestMatch(message, lexicon));
    }

    private Set<String> buildLocationLexicon(List<ObservationView> observationSamples, List<SpeciesView> speciesSamples) {
        Set<String> lexicon = new LinkedHashSet<>();
        locationAliasLookup().forEach((canonical, aliases) -> {
            lexicon.add(canonical);
            lexicon.addAll(aliases);
        });
        observationSamples.forEach(item -> {
            appendLocationTokens(lexicon, item.locationName());
            appendLocationTokens(lexicon, item.ecosystemName());
            appendLocationTokens(lexicon, item.note());
        });
        speciesSamples.forEach(item -> appendLocationTokens(lexicon, item.geoRangeText()));
        return lexicon;
    }

    private void appendLocationTokens(Set<String> lexicon, String source) {
        String normalized = simplifyLocationToken(source);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        lexicon.add(normalized);
        for (String segment : normalized.split("[，,、；;|/]")) {
            String token = simplifyLocationToken(segment);
            if (StringUtils.hasText(token)) {
                lexicon.add(token);
            }
        }
        Matcher matcher = LOCATION_TERM_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String token = simplifyLocationToken(matcher.group(1));
            if (StringUtils.hasText(token)) {
                lexicon.add(token);
            }
        }
        if (normalized.contains("南海北部")) {
            lexicon.add("南海北部");
        }
        if (normalized.contains("近岸样带")) {
            lexicon.add("近岸样带");
        }
        if (normalized.contains("湛江")) {
            lexicon.add("湛江");
        }
    }

    private String extractLocationBySuffix(String source) {
        for (String suffix : LOCATION_SUFFIXES) {
            int index = source.indexOf(suffix);
            if (index <= 0) {
                continue;
            }
            String snippet = source.substring(Math.max(0, index - 8), index).trim();
            snippet = snippet.replaceAll("(最近|过去|近)?[一二三四五六七八九十两0-9]+(年|天)", "");
            snippet = snippet.replace("在", "").replace("于", "").replace("到", "").trim();
            snippet = snippet.replace("最近", "").replace("过去", "").trim();
            if (snippet.length() >= 2) {
                return snippet;
            }
        }
        return null;
    }

    private boolean matchesLocationText(String source, String keyword) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(keyword)) {
            return false;
        }
        for (String alias : locationAliases(keyword)) {
            if (containsIgnoreCase(source, alias)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> locationAliases(String keyword) {
        Set<String> aliases = new LinkedHashSet<>();
        String normalizedKeyword = simplifyLocationToken(keyword);
        if (StringUtils.hasText(normalizedKeyword)) {
            aliases.add(normalizedKeyword);
        }
        for (Map.Entry<String, List<String>> entry : locationAliasLookup().entrySet()) {
            if (containsIgnoreCase(normalizedKeyword, entry.getKey())
                    || containsIgnoreCase(entry.getKey(), normalizedKeyword)
                    || entry.getValue().stream().anyMatch(alias -> containsIgnoreCase(normalizedKeyword, alias) || containsIgnoreCase(alias, normalizedKeyword))) {
                aliases.add(entry.getKey());
                aliases.addAll(entry.getValue());
            }
        }
        for (String suffix : LOCATION_SUFFIXES) {
            if (normalizedKeyword.endsWith(suffix) && normalizedKeyword.length() > suffix.length() + 1) {
                aliases.add(normalizedKeyword.substring(0, normalizedKeyword.length() - suffix.length()));
            }
        }
        if (normalizedKeyword.endsWith("北部") || normalizedKeyword.endsWith("南部")
                || normalizedKeyword.endsWith("东部") || normalizedKeyword.endsWith("西部")
                || normalizedKeyword.endsWith("中部")) {
            aliases.add(normalizedKeyword.substring(0, normalizedKeyword.length() - 2));
        }
        return aliases.stream().filter(StringUtils::hasText).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String simplifyLocationToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String normalized = token
                .replace('（', ' ')
                .replace('）', ' ')
                .replace('(', ' ')
                .replace(')', ' ')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        normalized = normalized.replaceAll("\\s*[A-Za-z]\\d+$", "");
        normalized = normalized.replaceAll("(最近|过去|近)?[一二三四五六七八九十两0-9]+(年|天)", "");
        normalized = normalized.replaceAll("^(在|于|到|从|把)", "");
        normalized = normalized.replaceAll("[，,、；;。]+$", "").trim();
        return normalized.length() < 2 ? null : normalized;
    }

    private String pickLongestMatch(String message, Collection<String> candidates) {
        return candidates.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(candidate -> containsIgnoreCase(message, candidate))
                .max(Comparator.comparingInt(String::length))
                .orElse(null);
    }

    private String buildNoDataAnswer(AssistantAiDtos.StructuredQuery plan, DashboardSummary dashboardSummary) {
        StringBuilder answer = new StringBuilder();
        answer.append("当前还没有检索到足够的数据来直接回答这个问题。")
                .append(" 目前系统共有物种档案 ")
                .append(dashboardSummary.totalSpecies())
                .append(" 条、观测记录 ")
                .append(dashboardSummary.totalObservations())
                .append(" 条、生态系统 ")
                .append(dashboardSummary.totalEcosystems())
                .append(" 个。");
        if (StringUtils.hasText(plan.locationKeyword())) {
            answer.append(" 在“").append(plan.locationKeyword()).append("”相关范围内暂未检索到满足条件的数据。");
        }
        if (StringUtils.hasText(plan.speciesKeyword())) {
            answer.append(" 建议尝试只保留物种中文名或学名再次查询。");
        } else {
            answer.append(" 你也可以换一种更具体的问法，比如补充地点、时间范围或生态系统名称。");
        }
        return answer.toString();
    }

    private String summarizeDistributionFeatures(List<SpeciesDetailView> species) {
        List<String> ranges = species.stream()
                .map(item -> firstNonBlank(item.geoRangeText(), item.distribution(), item.habitat()))
                .filter(StringUtils::hasText)
                .distinct()
                .limit(4)
                .toList();
        if (!ranges.isEmpty()) {
            return "分布范围主要集中在 " + String.join("、", ranges);
        }
        long withCoordinates = species.stream()
                .filter(item -> item.distributionLat() != null && item.distributionLng() != null)
                .count();
        if (withCoordinates > 0) {
            return "已有 " + withCoordinates + " 个物种带有明确坐标点，分布以近海与沿岸海域为主";
        }
        return "当前档案中的地理描述较少，暂时只能判断为以海洋与近岸环境分布为主";
    }

    private String summarizeTrendSentence(List<TrendPoint> trendPoints) {
        if (trendPoints.isEmpty()) {
            return "当前没有足够的月度趋势数据。";
        }
        TrendPoint first = trendPoints.get(0);
        TrendPoint last = trendPoints.get(trendPoints.size() - 1);
        TrendPoint peak = trendPoints.stream()
                .max(Comparator.comparingInt(TrendPoint::observationCount))
                .orElse(last);
        String direction = last.observationCount() > first.observationCount()
                ? "整体呈上升趋势"
                : last.observationCount() < first.observationCount()
                ? "整体呈回落趋势"
                : "整体相对平稳";
        String speciesPart = peak.speciesCount() > 0 ? "，关联物种数约 " + peak.speciesCount() + " 种" : "";
        return "从月度数据看，" + peak.month() + " 为观测高峰，共 " + peak.observationCount() + " 次" + speciesPart + "，" + direction + "。";
    }

    private String joinSpeciesNames(List<SpeciesDetailView> species, int limit) {
        return species.stream()
                .limit(limit)
                .map(item -> firstNonBlank(item.chineseName(), item.scientificName(), "未命名物种"))
                .collect(Collectors.joining("、"));
    }

    private String joinObservationLocations(List<ObservationView> observations, int limit) {
        List<String> locations = observations.stream()
                .map(item -> firstNonBlank(item.locationName(), item.ecosystemName(), "未命名地点"))
                .distinct()
                .limit(limit)
                .toList();
        return locations.isEmpty() ? "暂无明确地点信息" : String.join("、", locations);
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "-" : value.toLocalDate().format(DATE_FORMATTER);
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(keyword)) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    private boolean containsAny(String source, Collection<String> keywords) {
        if (!StringUtils.hasText(source)) {
            return false;
        }
        return keywords.stream().anyMatch(keyword -> containsIgnoreCase(source, keyword));
    }

    private String displaySpeciesName(SpeciesDetailView species) {
        String chineseName = safe(species.chineseName());
        String scientificName = safe(species.scientificName());
        if (StringUtils.hasText(chineseName) && StringUtils.hasText(scientificName)) {
            return chineseName + "（" + scientificName + "）";
        }
        return firstNonBlank(chineseName, scientificName, "该物种");
    }

    private String trimTrailingPunctuation(String value) {
        String trimmed = safe(value);
        while (trimmed.endsWith("。") || trimmed.endsWith("；") || trimmed.endsWith(";") || trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private int resolveLimit(Integer preferred, int fallback) {
        if (preferred == null || preferred <= 0) {
            return fallback;
        }
        return Math.min(Math.max(preferred, 1), 40);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isLightweightIntent(String intent) {
        String normalizedIntent = safe(intent);
        return "clarify".equals(normalizedIntent) || "capability_help".equals(normalizedIntent);
    }

    private String buildClarificationAnswer(AssistantAiDtos.ChatRequest request) {
        String previousQuestion = findLatestUserQuestion(request.history(), request.message());
        if (StringUtils.hasText(previousQuestion)) {
            return "这句追问还不够明确。"
                    + " 如果你是在继续上一题“"
                    + truncateForReply(previousQuestion, 18)
                    + "”，可以直接补一句“只看范围”“展开点位细节”或“按近30天再看一遍”，我就能顺着往下答。";
        }
        return "这句问题现在还太短，我暂时分不清你是想看地图范围、物种分布、观测记录，还是趋势分析。"
                + " 你可以补充地点、时间或对象，比如“这张生态地图覆盖哪些海域”或“近30天湛江附近有哪些观测记录”。";
    }

    private String buildCapabilityAnswer() {
        return "我更擅长帮你查这几类问题：物种分布与保护等级、某个海域或生态系统里的观测记录、近一段时间的变化趋势、以及谁的观测最活跃。"
                + " 你可以直接把地点、时间和对象一起说出来，我会先转成结构化查询，再给你一段更像科研助手的总结。";
    }

    private String findLatestUserQuestion(
            List<AssistantAiDtos.ConversationMessage> history,
            String currentMessage
    ) {
        PreviousUserTurn previousTurn = findLatestPreviousUserTurn(history, currentMessage);
        return previousTurn == null ? null : previousTurn.message();
    }

    private String truncateForReply(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength)) + "…";
    }

    private PreviousUserTurn findLatestPreviousUserTurn(
            List<AssistantAiDtos.ConversationMessage> history,
            String currentMessage
    ) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        String normalizedCurrent = normalize(currentMessage);
        for (int i = history.size() - 1; i >= 0; i--) {
            AssistantAiDtos.ConversationMessage item = history.get(i);
            if (item == null || !"user".equalsIgnoreCase(item.role()) || !StringUtils.hasText(item.content())) {
                continue;
            }
            String candidate = normalize(item.content());
            if (!candidate.equalsIgnoreCase(normalizedCurrent)) {
                return new PreviousUserTurn(candidate, List.copyOf(history.subList(0, i)));
            }
        }
        return null;
    }

    private record TrendPoint(String month, int observationCount, int speciesCount) {
    }

    private record ObservationActivity(String observerName, int count) {
    }

    private record PreviousUserTurn(
            String message,
            List<AssistantAiDtos.ConversationMessage> earlierHistory
    ) {
    }

    private record AssistantContext(
            DashboardSummary dashboardSummary,
            List<ObservationView> observationViews,
            List<ObservationDetailView> observations,
            List<SpeciesDetailView> species,
            List<TrendPoint> trendPoints,
            List<ObservationActivity> observerActivities,
            List<EcosystemAnalyticsPoint> ecosystemAnalytics
    ) {
    }
}
