package com.gsmv.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.gsmv.common.ErrorCode;
import com.gsmv.common.exception.BusinessException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AiModelGateway {

    private static final int MAX_VISION_IMAGE_DIMENSION = 1600;
    private static final int MAX_VISION_IMAGE_BYTES = 1_800_000;
    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private final AiProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final ObjectMapper lenientJsonMapper;

    public AiModelGateway(AiProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.lenientJsonMapper = objectMapper.copy()
                .configure(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature(), true)
                .configure(JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature(), true)
                .configure(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature(), true)
                .configure(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true)
                .configure(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature(), true)
                .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
                .configure(JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS.mappedFeature(), true);
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

    public List<List<Double>> embedTexts(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        String provider = embeddingProvider();
        if ("ollama".equalsIgnoreCase(provider)) {
            return embedTextsWithOllama(texts);
        }
        if ("bailian".equalsIgnoreCase(provider) || "dashscope".equalsIgnoreCase(provider)) {
            return embedTextsWithBailian(texts);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported embedding provider: " + provider, HttpStatus.BAD_REQUEST);
    }

    private List<List<Double>> embedTextsWithBailian(List<String> texts) {
        AiProperties.Bailian config = properties.bailian();
        requireConfigured(config.enabled(), config.apiKey(), "阿里云百炼 Embedding");
        if (texts.size() > 10) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Embedding 每批最多支持 10 段文本", HttpStatus.BAD_REQUEST);
        }

        String model = StringUtils.hasText(config.embeddingModel()) ? config.embeddingModel() : "text-embedding-v4";
        Integer dimension = config.embeddingDimension() == null ? 1024 : config.embeddingDimension();
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", texts,
                "dimensions", dimension
        );

        RestClient client = restClientBuilder
                .baseUrl(config.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                String responseBody = client.post()
                        .uri("/embeddings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);
                JsonNode response = objectMapper.readTree(responseBody);
                List<List<Double>> embeddings = new ArrayList<>();
                for (JsonNode item : response.path("data")) {
                    List<Double> vector = new ArrayList<>();
                    for (JsonNode value : item.path("embedding")) {
                        vector.add(value.asDouble());
                    }
                    embeddings.add(vector);
                }
                if (embeddings.size() != texts.size()) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Embedding 返回数量与输入不一致", HttpStatus.BAD_GATEWAY);
                }
                return embeddings;
            } catch (RestClientResponseException ex) {
                if (attempt < MAX_RETRY_ATTEMPTS && shouldRetry(ex)) {
                    sleepQuietly(350L * attempt);
                    continue;
                }
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "Embedding 服务调用失败: " + readableErrorMessage(ex),
                        HttpStatus.BAD_GATEWAY
                );
            } catch (BusinessException ex) {
                throw ex;
            } catch (Exception ex) {
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    sleepQuietly(350L * attempt);
                    continue;
                }
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Embedding 服务调用失败: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
            }
        }

        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Embedding 服务调用失败，请稍后重试", HttpStatus.BAD_GATEWAY);
    }

    private List<List<Double>> embedTextsWithOllama(List<String> texts) {
        AiProperties.Ollama config = ollamaConfig();
        requireEnabled(config.enabled(), "Ollama Embedding");
        if (texts.size() > 10) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Embedding batch size must be no more than 10", HttpStatus.BAD_REQUEST);
        }

        String model = StringUtils.hasText(config.embeddingModel()) ? config.embeddingModel() : "bge-m3";
        Integer dimension = config.embeddingDimension() == null ? 1024 : config.embeddingDimension();
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", texts
        );

        RestClient client = restClientBuilder
                .baseUrl(StringUtils.hasText(config.baseUrl()) ? config.baseUrl() : "http://localhost:11434")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                String responseBody = client.post()
                        .uri("/api/embed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);
                JsonNode response = objectMapper.readTree(responseBody);
                return parseOllamaEmbeddings(response, texts.size(), dimension);
            } catch (RestClientResponseException ex) {
                if (attempt < MAX_RETRY_ATTEMPTS && shouldRetry(ex)) {
                    sleepQuietly(350L * attempt);
                    continue;
                }
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "Ollama embedding call failed: " + readableErrorMessage(ex),
                        HttpStatus.BAD_GATEWAY
                );
            } catch (BusinessException ex) {
                throw ex;
            } catch (Exception ex) {
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    sleepQuietly(350L * attempt);
                    continue;
                }
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Ollama embedding call failed: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
            }
        }

        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Ollama embedding call failed, please retry later", HttpStatus.BAD_GATEWAY);
    }

    public JsonNode bailianVisionJson(String systemPrompt, String userPrompt, byte[] imageBytes, String contentType) {
        AiProperties.Bailian config = properties.bailian();
        requireConfigured(config.enabled(), config.apiKey(), "阿里云百炼");
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先上传需要识别的图片", HttpStatus.BAD_REQUEST);
        }

        NormalizedVisionImage normalizedImage = normalizeVisionImage(imageBytes, contentType);
        String dataUrl = "data:" + normalizedImage.contentType() + ";base64," + Base64.getEncoder().encodeToString(normalizedImage.bytes());

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

    private List<List<Double>> parseOllamaEmbeddings(JsonNode response, int expectedCount, Integer expectedDimension) {
        JsonNode embeddingsNode = response.path("embeddings");
        if (embeddingsNode.isMissingNode() && response.has("embedding")) {
            embeddingsNode = objectMapper.createArrayNode().add(response.path("embedding"));
        }
        if (!embeddingsNode.isArray()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Ollama embedding response missing embeddings", HttpStatus.BAD_GATEWAY);
        }

        List<List<Double>> embeddings = new ArrayList<>();
        for (JsonNode item : embeddingsNode) {
            List<Double> vector = new ArrayList<>();
            for (JsonNode value : item) {
                vector.add(value.asDouble());
            }
            embeddings.add(vector);
        }
        if (embeddings.size() != expectedCount) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Ollama embedding count mismatch", HttpStatus.BAD_GATEWAY);
        }
        if (expectedDimension != null && expectedDimension > 0) {
            for (List<Double> vector : embeddings) {
                if (vector.size() != expectedDimension) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Ollama embedding dimension mismatch", HttpStatus.BAD_GATEWAY);
                }
            }
        }
        return embeddings;
    }

    private String embeddingProvider() {
        if (properties.embedding() != null && StringUtils.hasText(properties.embedding().provider())) {
            return properties.embedding().provider();
        }
        return "ollama";
    }

    private AiProperties.Ollama ollamaConfig() {
        if (properties.ollama() != null) {
            return properties.ollama();
        }
        return new AiProperties.Ollama(true, "http://localhost:11434", "bge-m3", 1024);
    }

    private JsonNode requestJson(String baseUrl, String apiKey, Map<String, Object> requestBody) {
        String content = requestText(baseUrl, apiKey, requestBody);
        return parseJsonContent(content);
    }

    private String requestText(String baseUrl, String apiKey, Map<String, Object> requestBody) {
        RestClient client = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                String responseBody = client.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);
                JsonNode response = objectMapper.readTree(responseBody);
                return extractMessageContent(response);
            } catch (RestClientResponseException ex) {
                if (attempt < MAX_RETRY_ATTEMPTS && shouldRetry(ex)) {
                    sleepQuietly(350L * attempt);
                    continue;
                }
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "AI 服务调用失败: " + readableErrorMessage(ex),
                        HttpStatus.BAD_GATEWAY
                );
            } catch (Exception ex) {
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    sleepQuietly(350L * attempt);
                    continue;
                }
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务调用失败: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
            }
        }

        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务调用失败，请稍后重试", HttpStatus.BAD_GATEWAY);
    }

    private JsonNode parseJsonContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务未返回可解析内容", HttpStatus.BAD_GATEWAY);
        }
        String cleaned = cleanJsonText(content);
        try {
            return objectMapper.readTree(cleaned);
        } catch (IOException ignored) {
            try {
                return lenientJsonMapper.readTree(cleaned);
            } catch (IOException ex) {
                JsonNode fallbackNode = parseStructuredTextFallback(cleaned);
                if (fallbackNode != null) {
                    return fallbackNode;
                }
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 返回结果解析失败: " + content, HttpStatus.BAD_GATEWAY);
            }
        } catch (Exception ex) {
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

    private void requireEnabled(Boolean enabled, String providerName) {
        if (enabled != null && !enabled) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    providerName + " is disabled",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    private boolean shouldRetry(RestClientResponseException ex) {
        return ex.getStatusCode().is5xxServerError() || ex.getStatusCode().value() == 429;
    }

    private String readableErrorMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (!StringUtils.hasText(body)) {
            return ex.getMessage();
        }

        try {
            JsonNode node = objectMapper.readTree(body);
            String nestedMessage = firstNonBlank(
                    node.path("message").asText(""),
                    node.path("error").path("message").asText(""),
                    node.path("error").asText(""),
                    node.path("msg").asText("")
            );
            if (StringUtils.hasText(nestedMessage)) {
                return nestedMessage;
            }
        } catch (Exception ignored) {
            // fall through and use raw body
        }

        return body.length() > 240 ? body.substring(0, 240) + "..." : body;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private NormalizedVisionImage normalizeVisionImage(byte[] imageBytes, String contentType) {
        String safeContentType = StringUtils.hasText(contentType) ? contentType.trim().toLowerCase() : MediaType.IMAGE_JPEG_VALUE;
        boolean supportedDirectType = safeContentType.equals(MediaType.IMAGE_JPEG_VALUE)
                || safeContentType.equals(MediaType.IMAGE_PNG_VALUE)
                || safeContentType.equals("image/webp");

        try {
            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (sourceImage == null) {
                return new NormalizedVisionImage(imageBytes, supportedDirectType ? safeContentType : MediaType.IMAGE_JPEG_VALUE);
            }

            int sourceWidth = sourceImage.getWidth();
            int sourceHeight = sourceImage.getHeight();
            double scaleRatio = Math.min(
                    1.0d,
                    (double) MAX_VISION_IMAGE_DIMENSION / Math.max(sourceWidth, sourceHeight)
            );
            boolean needsResize = scaleRatio < 1.0d;
            boolean needsReencode = !supportedDirectType || imageBytes.length > MAX_VISION_IMAGE_BYTES || needsResize;

            if (!needsReencode) {
                return new NormalizedVisionImage(imageBytes, safeContentType);
            }

            int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scaleRatio));
            int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scaleRatio));
            BufferedImage targetImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

            Graphics2D graphics = targetImage.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(sourceImage, 0, 0, targetWidth, targetHeight, null);
            graphics.dispose();

            byte[] compressed = writeJpeg(targetImage, imageBytes.length > 4_000_000 ? 0.72f : 0.82f);
            if (compressed.length > MAX_VISION_IMAGE_BYTES) {
                compressed = writeJpeg(targetImage, 0.64f);
            }
            return new NormalizedVisionImage(compressed, MediaType.IMAGE_JPEG_VALUE);
        } catch (Exception ignored) {
            return new NormalizedVisionImage(imageBytes, supportedDirectType ? safeContentType : MediaType.IMAGE_JPEG_VALUE);
        }
    }

    private byte[] writeJpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        if (writeParam.canWriteCompressed()) {
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(quality);
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream imageOutputStream = new MemoryCacheImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);
            writer.write(null, new IIOImage(image, null, null), writeParam);
            writer.dispose();
            return outputStream.toByteArray();
        }
    }

    private record NormalizedVisionImage(byte[] bytes, String contentType) {
    }

    private JsonNode parseStructuredTextFallback(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }

        var root = objectMapper.createObjectNode();
        putIfPresent(root, "likelyChineseName", extractStringField(content, "likelyChineseName", "chineseName"));
        putIfPresent(root, "likelyScientificName", extractStringField(content, "likelyScientificName", "scientificName"));
        putIfPresent(root, "reasoning", extractStringField(content, "reasoning", "reason"));

        Double confidence = extractNumberField(content, "confidence");
        if (confidence != null) {
            root.put("confidence", confidence);
        }

        String candidatesBlock = extractBracketBlock(content, "candidates", '[', ']');
        if (StringUtils.hasText(candidatesBlock)) {
            var candidates = objectMapper.createArrayNode();
            for (String candidateBlock : splitObjectBlocks(candidatesBlock)) {
                var candidateNode = objectMapper.createObjectNode();
                putIfPresent(candidateNode, "chineseName", extractStringField(candidateBlock, "chineseName"));
                putIfPresent(candidateNode, "scientificName", extractStringField(candidateBlock, "scientificName"));
                putIfPresent(candidateNode, "reason", extractStringField(candidateBlock, "reason", "reasoning"));
                Double candidateConfidence = extractNumberField(candidateBlock, "confidence");
                if (candidateConfidence != null) {
                    candidateNode.put("confidence", candidateConfidence);
                }
                if (!candidateNode.isEmpty()) {
                    candidates.add(candidateNode);
                }
            }
            if (!candidates.isEmpty()) {
                root.set("candidates", candidates);
            }
        }

        return root.isEmpty() ? null : root;
    }

    private void putIfPresent(com.fasterxml.jackson.databind.node.ObjectNode node, String fieldName, String value) {
        if (StringUtils.hasText(value)) {
            node.put(fieldName, value);
        }
    }

    private String extractStringField(String content, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String doubleQuoted = matchGroup(content, "(?is)[\"']?" + Pattern.quote(fieldName) + "[\"']?\\s*[:=]\\s*\"((?:\\\\.|[^\"])*)\"");
            if (StringUtils.hasText(doubleQuoted)) {
                return unescapeText(doubleQuoted.trim());
            }

            String singleQuoted = matchGroup(content, "(?is)[\"']?" + Pattern.quote(fieldName) + "[\"']?\\s*[:=]\\s*'((?:\\\\.|[^'])*)'");
            if (StringUtils.hasText(singleQuoted)) {
                return unescapeText(singleQuoted.trim());
            }

            String bareValue = matchGroup(content, "(?is)[\"']?" + Pattern.quote(fieldName) + "[\"']?\\s*[:=]\\s*([A-Za-z0-9_.\\-\\u4e00-\\u9fa5]+)");
            if (StringUtils.hasText(bareValue)) {
                return bareValue.trim();
            }
        }
        return null;
    }

    private Double extractNumberField(String content, String fieldName) {
        String raw = matchGroup(content, "(?is)[\"']?" + Pattern.quote(fieldName) + "[\"']?\\s*[:=]\\s*([-+]?\\d+(?:\\.\\d+)?)");
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        return Double.parseDouble(matcher.group());
    }

    private String matchGroup(String content, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractBracketBlock(String content, String fieldName, char open, char close) {
        Matcher matcher = Pattern.compile("(?is)[\"']?" + Pattern.quote(fieldName) + "[\"']?\\s*[:=]\\s*\\" + open).matcher(content);
        if (!matcher.find()) {
            return null;
        }

        int start = matcher.end() - 1;
        int depth = 0;
        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;

        for (int index = start; index < content.length(); index++) {
            char current = content.charAt(index);
            char previous = index > 0 ? content.charAt(index - 1) : '\0';

            if (current == '"' && previous != '\\' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (current == '\'' && previous != '\\' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            }

            if (inDoubleQuotes || inSingleQuotes) {
                continue;
            }

            if (current == open) {
                depth++;
            } else if (current == close) {
                depth--;
                if (depth == 0) {
                    return content.substring(start + 1, index);
                }
            }
        }

        return null;
    }

    private List<String> splitObjectBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        int depth = 0;
        int blockStart = -1;
        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            char previous = index > 0 ? content.charAt(index - 1) : '\0';

            if (current == '"' && previous != '\\' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (current == '\'' && previous != '\\' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            }

            if (inDoubleQuotes || inSingleQuotes) {
                continue;
            }

            if (current == '{') {
                if (depth == 0) {
                    blockStart = index + 1;
                }
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0 && blockStart >= 0) {
                    blocks.add(content.substring(blockStart, index));
                    blockStart = -1;
                }
            }
        }

        return blocks;
    }

    private String unescapeText(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r");
    }
}
