package com.gsmv.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsmv.common.ErrorCode;
import com.gsmv.common.exception.BusinessException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AiModelGateway {

    private final AiProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public AiModelGateway(AiProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    public JsonNode deepSeekJson(List<Map<String, Object>> messages) {
        AiProperties.DeepSeek config = properties.deepseek();
        requireConfigured(config.enabled(), config.apiKey(), "DeepSeek");

        List<Map<String, Object>> payloadMessages = new ArrayList<>(messages);
        return requestJson(config.baseUrl(), config.apiKey(), Map.of(
                "model", config.chatModel(),
                "messages", payloadMessages,
                "response_format", Map.of("type", "json_object")
        ));
    }

    public String deepSeekText(List<Map<String, Object>> messages) {
        AiProperties.DeepSeek config = properties.deepseek();
        requireConfigured(config.enabled(), config.apiKey(), "DeepSeek");

        List<Map<String, Object>> payloadMessages = new ArrayList<>(messages);
        return requestText(config.baseUrl(), config.apiKey(), Map.of(
                "model", config.chatModel(),
                "messages", payloadMessages
        ));
    }

    public JsonNode bailianVisionJson(String systemPrompt, String userPrompt, byte[] imageBytes, String contentType) {
        AiProperties.Bailian config = properties.bailian();
        requireConfigured(config.enabled(), config.apiKey(), "阿里云百炼");
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先上传需要识别的图片", HttpStatus.BAD_REQUEST);
        }

        String safeContentType = StringUtils.hasText(contentType) ? contentType : MediaType.IMAGE_JPEG_VALUE;
        String dataUrl = "data:" + safeContentType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        return requestJson(config.baseUrl(), config.apiKey(), Map.of(
                "model", config.visionModel(),
                "messages", List.of(
                        message("system", systemPrompt),
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of("type", "text", "text", userPrompt),
                                        Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
                                )
                        )
                ),
                "response_format", Map.of("type", "json_object")
        ));
    }

    public static Map<String, Object> message(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    private JsonNode requestJson(String baseUrl, String apiKey, Map<String, Object> requestBody) {
        String content = requestText(baseUrl, apiKey, requestBody);
        return parseJsonContent(content);
    }

    private String requestText(String baseUrl, String apiKey, Map<String, Object> requestBody) {
        try {
            RestClient client = restClientBuilder
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
            String responseBody = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            JsonNode response = objectMapper.readTree(responseBody);
            return extractMessageContent(response);
        } catch (RestClientResponseException ex) {
            String message = ex.getResponseBodyAsString();
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "AI 服务调用失败: " + (StringUtils.hasText(message) ? message : ex.getMessage()),
                    HttpStatus.BAD_GATEWAY
            );
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务调用失败: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private JsonNode parseJsonContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务未返回可解析内容", HttpStatus.BAD_GATEWAY);
        }
        try {
            return objectMapper.readTree(cleanJsonText(content));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 返回结果解析失败: " + content, HttpStatus.BAD_GATEWAY);
        }
    }

    private String cleanJsonText(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }

        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }

        int arrayStart = trimmed.indexOf('[');
        int arrayEnd = trimmed.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1);
        }

        return trimmed;
    }

    private String extractMessageContent(JsonNode response) {
        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务响应缺少内容", HttpStatus.BAD_GATEWAY);
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : content) {
                if (item.path("type").asText("").equals("text")) {
                    builder.append(item.path("text").asText(""));
                } else if (item.isTextual()) {
                    builder.append(item.asText());
                }
            }
            return builder.toString();
        }
        return content.toString();
    }

    private void requireConfigured(boolean enabled, String apiKey, String providerName) {
        if (!enabled || !StringUtils.hasText(apiKey)) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    providerName + " 未配置可用 API Key，请先设置环境变量后再使用智能服务",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }
}
