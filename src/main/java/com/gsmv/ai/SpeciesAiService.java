package com.gsmv.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.gsmv.ai.dto.SpeciesAiDtos;
import com.gsmv.ai.rag.RagKnowledgeService;
import com.gsmv.ai.rag.RagSearchHit;
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

    private final AiModelGateway aiModelGateway;
    private final AiProperties aiProperties;
    private final SpeciesService speciesService;
    private final RagKnowledgeService ragKnowledgeService;
    private final AuditService auditService;

    public SpeciesAiService(
            AiModelGateway aiModelGateway,
            AiProperties aiProperties,
            SpeciesService speciesService,
            RagKnowledgeService ragKnowledgeService,
            AuditService auditService
    ) {
        this.aiModelGateway = aiModelGateway;
        this.aiProperties = aiProperties;
        this.speciesService = speciesService;
        this.ragKnowledgeService = ragKnowledgeService;
        this.auditService = auditService;
    }

    public SpeciesAiDtos.IdentifyImageResponse identifyImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先上传海洋生物图片", HttpStatus.BAD_REQUEST);
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持图片文件用于智能识别", HttpStatus.BAD_REQUEST);
        }

        try {
            JsonNode result = aiModelGateway.bailianVisionJson(
                    """
                    你是一名海洋生物图像识别专家。
                    请根据图片内容给出最可能的物种识别结果，并仅返回 JSON。
                    """,
                    """
                    请识别这张海洋生物图片，并返回如下 JSON：
                    {
                      "likelyChineseName": "",
                      "likelyScientificName": "",
                      "confidence": 0.0,
                      "reasoning": "",
                      "candidates": [
                        {"chineseName": "", "scientificName": "", "confidence": 0.0, "reason": ""}
                      ]
                    }
                    规则：
                    1. confidence 范围为 0 到 1。
                    2. candidates 返回 1 到 5 个候选项，按置信度降序。
                    3. 若无法确定，请降低 confidence，并在 reasoning 中说明不确定原因。
                    4. 输出必须是合法 JSON，不要输出 Markdown。
                    """,
                    file.getBytes(),
                    file.getContentType()
            );

            String likelyChineseName = text(result, "likelyChineseName");
            String likelyScientificName = text(result, "likelyScientificName");
            double confidence = boundedConfidence(number(result, "confidence"));
            String reasoning = text(result, "reasoning");
            List<SpeciesAiDtos.IdentificationCandidate> candidates = parseCandidates(result.path("candidates"));
            boolean modelNeedsHumanReview = confidence < aiProperties.lowConfidenceThreshold();
            List<String> keywords = new ArrayList<>();
            keywords.add(likelyChineseName);
            keywords.add(likelyScientificName);
            candidates.forEach(candidate -> {
                keywords.add(candidate.chineseName());
                keywords.add(candidate.scientificName());
            });
            List<SpeciesAiDtos.RelatedSpeciesRecord> relatedSpeciesRecords = searchRelatedSpecies(keywords.toArray(String[]::new));
            String ragQuery = String.join(" ", keywords.stream().filter(StringUtils::hasText).toList());
            List<com.gsmv.ai.rag.dto.RagDtos.RagEvidenceItem> ragEvidence = ragKnowledgeService.retrieveEvidenceForScenario(
                    RagKnowledgeService.SCENARIO_IMAGE_IDENTIFICATION,
                    firstNonBlank(ragQuery, reasoning, "marine species image identification"),
                    5
            );
            boolean confidenceAdjustedByRag = !ragEvidence.isEmpty();
            List<String> conflictWarnings = imageRagWarnings(confidence, ragEvidence, likelyChineseName, likelyScientificName);
            double finalConfidence = confidenceAdjustedByRag && conflictWarnings.isEmpty()
                    ? Math.min(1.0d, confidence + 0.05d)
                    : confidence;
            boolean needsHumanReview = modelNeedsHumanReview || finalConfidence < aiProperties.lowConfidenceThreshold() || !conflictWarnings.isEmpty();
            String ragConclusion = ragEvidence.isEmpty()
                    ? "No reliable RAG evidence was retrieved for this image result."
                    : "RAG retrieved " + ragEvidence.size() + " evidence item(s) for candidate verification.";

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
                    likelyChineseName,
                    likelyScientificName,
                    finalConfidence,
                    needsHumanReview,
                    needsHumanReview ? "建议人工复核" : "识别置信度较高",
                    reasoning,
                    candidates,
                    relatedSpeciesRecords,
                    ragEvidence,
                    confidenceAdjustedByRag,
                    ragConclusion,
                    conflictWarnings
            );
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取识别图片失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public SpeciesAiDtos.AutocompleteResponse autocomplete(SpeciesAiDtos.AutocompleteRequest request) {
        if (!StringUtils.hasText(request.chineseName()) && !StringUtils.hasText(request.scientificName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少填写中文名或学名后再使用 AI 补全", HttpStatus.BAD_REQUEST);
        }

        JsonNode result = aiModelGateway.deepSeekJson(List.of(
                AiModelGateway.message("system", """
                        你是一名海洋生物分类学与科研写作助手。
                        你的任务是根据已知的中文名、学名和已有描述，补全物种档案字段。
                        返回内容必须是纯 JSON。
                        """),
                AiModelGateway.message("user", """
                        请根据以下输入补全物种档案。
                        输入：
                        中文名：%s
                        学名：%s
                        物种简介：%s
                        形态特征：%s
                        生活习性：%s
                        栖息环境：%s
                        分布区域：%s
                        地理范围：%s
                        RAG知识库参考：
                        %s

                        请严格返回 JSON：
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

                        规则：
                        1. 尽量保留用户已输入的中文名和学名。
                        2. 不确定的字段请返回空字符串，不要编造。
                        3. 文本内容使用简洁、规范的中文。
                        4. confidence 范围 0 到 1。
                        """.formatted(
                        safe(request.chineseName()),
                        safe(request.scientificName()),
                        safe(request.description()),
                        safe(request.morphology()),
                        safe(request.habit()),
                        safe(request.habitat()),
                        safe(request.distribution()),
                        safe(request.geoRangeText()),
                        ragContext(firstNonBlank(request.chineseName(), request.scientificName(), request.description()))
                ))
        ));

        List<SpeciesAiDtos.RelatedSpeciesRecord> related = searchRelatedSpecies(request.chineseName(), request.scientificName());
        auditService.record(SecurityUtils.requireCurrentUser().userId(), "AI", "AUTOCOMPLETE_SPECIES", "SPECIES", null, true,
                "{\"chineseName\":\"" + escapeJson(request.chineseName()) + "\",\"scientificName\":\"" + escapeJson(request.scientificName()) + "\"}");
        return new SpeciesAiDtos.AutocompleteResponse(
                fallback(text(result, "chineseName"), request.chineseName()),
                fallback(text(result, "scientificName"), request.scientificName()),
                text(result, "phylumName"),
                text(result, "className"),
                text(result, "orderName"),
                text(result, "familyName"),
                text(result, "genusName"),
                text(result, "protectionLevel"),
                text(result, "iucnStatus"),
                fallback(text(result, "description"), request.description()),
                fallback(text(result, "morphology"), request.morphology()),
                fallback(text(result, "habit"), request.habit()),
                fallback(text(result, "habitat"), request.habitat()),
                fallback(text(result, "distribution"), request.distribution()),
                fallback(text(result, "geoRangeText"), request.geoRangeText()),
                text(result, "summary"),
                boundedConfidence(number(result, "confidence")),
                stringList(result.path("notes")),
                related
        );
    }

    public SpeciesAiDtos.PolishTextResponse polishText(SpeciesAiDtos.PolishTextRequest request) {
        JsonNode result = aiModelGateway.deepSeekJson(List.of(
                AiModelGateway.message("system", """
                        你是一名海洋生物科研文本编辑助手。
                        你会对输入文本进行规范化润色，并返回纯 JSON。
                        """),
                AiModelGateway.message("user", """
                        请润色字段“%s”的内容，使其更规范、简洁、适合物种档案使用。
                        原文：
                        %s
                        RAG知识库参考：
                        %s

                        请返回 JSON：
                        {
                          "polishedText": "",
                          "summary": "",
                          "keywords": []
                        }
                        """.formatted(request.fieldName(), request.text(), ragContext(request.text())))
        ));

        auditService.record(SecurityUtils.requireCurrentUser().userId(), "AI", "POLISH_SPECIES_TEXT", "SPECIES", null, true,
                "{\"field\":\"" + escapeJson(request.fieldName()) + "\"}");
        return new SpeciesAiDtos.PolishTextResponse(
                request.fieldName(),
                text(result, "polishedText"),
                text(result, "summary"),
                stringList(result.path("keywords"))
        );
    }

    public SpeciesAiDtos.TranslateSpeciesResponse translate(SpeciesAiDtos.TranslateSpeciesRequest request) {
        JsonNode result = aiModelGateway.deepSeekJson(List.of(
                AiModelGateway.message("system", """
                        你是一名多语言海洋生物科普翻译助手。
                        请把用户提供的描述翻译为目标语言，并返回纯 JSON。
                        """),
                AiModelGateway.message("user", """
                        目标语言：%s
                        中文名：%s
                        学名：%s
                        物种简介：%s
                        形态特征：%s
                        生活习性：%s
                        栖息环境：%s
                        分布区域：%s
                        地理范围：%s
                        RAG知识库参考：
                        %s

                        请返回 JSON：
                        {
                          "description": "",
                          "morphology": "",
                          "habit": "",
                          "habitat": "",
                          "distribution": "",
                          "geoRangeText": "",
                          "summary": ""
                        }

                        规则：
                        1. 学名不翻译。
                        2. 术语保持科研表达准确。
                        3. 若某字段为空，返回空字符串。
                        """.formatted(
                        request.targetLanguage(),
                        safe(request.chineseName()),
                        safe(request.scientificName()),
                        safe(request.description()),
                        safe(request.morphology()),
                        safe(request.habit()),
                        safe(request.habitat()),
                        safe(request.distribution()),
                        safe(request.geoRangeText()),
                        ragContext(firstNonBlank(request.chineseName(), request.scientificName(), request.description(), request.distribution()))
                ))
        ));

        auditService.record(SecurityUtils.requireCurrentUser().userId(), "AI", "TRANSLATE_SPECIES", "SPECIES", null, true,
                "{\"targetLanguage\":\"" + escapeJson(request.targetLanguage()) + "\"}");
        return new SpeciesAiDtos.TranslateSpeciesResponse(
                request.targetLanguage(),
                text(result, "description"),
                text(result, "morphology"),
                text(result, "habit"),
                text(result, "habitat"),
                text(result, "distribution"),
                text(result, "geoRangeText"),
                text(result, "summary")
        );
    }

    private List<SpeciesAiDtos.IdentificationCandidate> parseCandidates(JsonNode candidatesNode) {
        List<SpeciesAiDtos.IdentificationCandidate> candidates = new ArrayList<>();
        if (!candidatesNode.isArray()) {
            return candidates;
        }
        for (JsonNode item : candidatesNode) {
            candidates.add(new SpeciesAiDtos.IdentificationCandidate(
                    text(item, "chineseName"),
                    text(item, "scientificName"),
                    boundedConfidence(number(item, "confidence")),
                    text(item, "reason")
            ));
        }
        return candidates;
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

    private List<String> imageRagWarnings(
            double confidence,
            List<com.gsmv.ai.rag.dto.RagDtos.RagEvidenceItem> evidence,
            String likelyChineseName,
            String likelyScientificName
    ) {
        List<String> warnings = new ArrayList<>();
        if (evidence.isEmpty() && confidence < 0.78d) {
            warnings.add("未检索到可靠RAG证据，建议人工复核");
            return warnings;
        }
        if (!evidence.isEmpty()) {
            String joined = evidence.stream()
                    .map(item -> safe(item.title()) + " " + safe(item.summary()) + " " + safe(item.contentSnippet()))
                    .toList()
                    .toString()
                    .toLowerCase();
            boolean matchedChinese = StringUtils.hasText(likelyChineseName) && joined.contains(likelyChineseName.toLowerCase());
            boolean matchedScientific = StringUtils.hasText(likelyScientificName) && joined.contains(likelyScientificName.toLowerCase());
            if (!matchedChinese && !matchedScientific && confidence < 0.82d) {
                warnings.add("识别候选与知识库证据匹配较弱，建议人工复核");
            }
        }
        return warnings;
    }

    private String text(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() || fieldNode.isNull() ? "" : fieldNode.asText("").trim();
    }

    private String ragContext(String query) {
        if (!StringUtils.hasText(query)) {
            return "无";
        }
        List<RagSearchHit> hits = ragKnowledgeService.retrieveForScenario(RagKnowledgeService.SCENARIO_SPECIES_PROFILE, query, 4);
        if (hits.isEmpty()) {
            return "无";
        }
        return hits.stream()
                .map(hit -> hit.title() + "：" + firstNonBlank(hit.summary(), hit.content()))
                .toList()
                .toString();
    }

    private double number(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isNumber() ? fieldNode.asDouble() : 0.0d;
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
}
