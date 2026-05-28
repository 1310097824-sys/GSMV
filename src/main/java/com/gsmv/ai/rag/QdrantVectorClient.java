package com.gsmv.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class QdrantVectorClient {
    private static final Logger log = LoggerFactory.getLogger(QdrantVectorClient.class);

    private final RagProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public QdrantVectorClient(RagProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.qdrant().enabled() == null || properties.qdrant().enabled();
    }

    public QdrantStatus status() {
        if (!isEnabled()) {
            return new QdrantStatus(false, "DISABLED", 0L, "Qdrant is disabled");
        }
        try {
            String response = client().get()
                    .uri("/collections/{collection}", collection())
                    .retrieve()
                    .body(String.class);
            JsonNode collection = readJson(response);
            long points = collection == null ? 0L : collection.path("result").path("points_count").asLong(0L);
            return new QdrantStatus(true, "READY", points, null);
        } catch (RestClientResponseException ex) {
            return new QdrantStatus(false, "UNAVAILABLE", 0L, ex.getStatusCode() + " " + ex.getResponseBodyAsString());
        } catch (RuntimeException ex) {
            return new QdrantStatus(false, "UNAVAILABLE", 0L, ex.getMessage());
        }
    }

    public boolean ensureCollection() {
        if (!isEnabled()) {
            return false;
        }
        QdrantStatus current = status();
        if (current.available()) {
            return true;
        }
        return createCollection();
    }

    public boolean recreateCollection() {
        if (!isEnabled()) {
            return false;
        }
        try {
            client().delete()
                    .uri("/collections/{collection}", collection())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() != 404) {
                log.warn("Qdrant collection delete failed: {}", ex.getResponseBodyAsString());
                return false;
            }
        } catch (RuntimeException ex) {
            log.warn("Qdrant collection delete failed: {}", ex.getMessage());
            return false;
        }
        return createCollection();
    }

    private boolean createCollection() {
        try {
            client().put()
                    .uri("/collections/{collection}", collection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "vectors", Map.of(
                                    "size", dimension(),
                                    "distance", "Cosine"
                            )
                    ))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException ex) {
            log.warn("Qdrant collection create failed: {}", ex.getResponseBodyAsString());
            return false;
        } catch (RuntimeException ex) {
            log.warn("Qdrant collection create failed: {}", ex.getMessage());
            return false;
        }
    }

    public boolean upsert(List<QdrantPoint> points) {
        if (points == null || points.isEmpty() || !ensureCollection()) {
            return false;
        }
        try {
            List<Map<String, Object>> payloadPoints = points.stream()
                    .map(point -> Map.of(
                            "id", point.id(),
                            "vector", point.vector(),
                            "payload", point.payload()
                    ))
                    .toList();
            client().put()
                    .uri("/collections/{collection}/points?wait=true", collection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("points", payloadPoints))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException ex) {
            log.warn("Qdrant upsert failed: {}", ex.getResponseBodyAsString());
            return false;
        } catch (RuntimeException ex) {
            log.warn("Qdrant upsert failed: {}", ex.getMessage());
            return false;
        }
    }

    public boolean deleteByDocumentId(Long documentId) {
        if (documentId == null || !isEnabled()) {
            return false;
        }
        try {
            client().post()
                    .uri("/collections/{collection}/points/delete?wait=true", collection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "filter", Map.of(
                                    "must", List.of(Map.of(
                                            "key", "documentId",
                                            "match", Map.of("value", documentId)
                                    ))
                            )
                    ))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException ex) {
            log.warn("Qdrant document points delete failed: {}", ex.getResponseBodyAsString());
            return false;
        } catch (RuntimeException ex) {
            log.warn("Qdrant document points delete failed: {}", ex.getMessage());
            return false;
        }
    }

    public List<QdrantSearchHit> search(List<Double> vector, int limit, Map<String, Object> filter) {
        if (vector == null || vector.isEmpty() || !isEnabled()) {
            return List.of();
        }
        try {
            Map<String, Object> body = StringUtils.hasText(writeFilter(filter))
                    ? Map.of("vector", vector, "limit", limit, "with_payload", true, "filter", filter)
                    : Map.of("vector", vector, "limit", limit, "with_payload", true);
            String response = client().post()
                    .uri("/collections/{collection}/points/search", collection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode responseJson = readJson(response);
            List<QdrantSearchHit> hits = new ArrayList<>();
            if (responseJson != null) {
                for (JsonNode item : responseJson.path("result")) {
                    Long chunkId = readLongPayload(item.path("payload").path("chunkId"));
                    if (chunkId != null) {
                        hits.add(new QdrantSearchHit(chunkId, item.path("score").asDouble(0.0d)));
                    }
                }
            }
            return hits;
        } catch (RestClientResponseException ex) {
            log.warn("Qdrant search failed: {}", ex.getResponseBodyAsString());
            return List.of();
        } catch (RuntimeException ex) {
            log.warn("Qdrant search failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private JsonNode readJson(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Qdrant response parse failed: " + ex.getMessage(), ex);
        }
    }

    private Long readLongPayload(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.canConvertToLong()) {
            return value.asLong();
        }
        if (value.isTextual()) {
            try {
                return Long.parseLong(value.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String writeFilter(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(filter);
        } catch (Exception ignored) {
            return "";
        }
    }

    private RestClient client() {
        return restClientBuilder.baseUrl(properties.qdrant().url()).build();
    }

    private String collection() {
        return StringUtils.hasText(properties.qdrant().collection()) ? properties.qdrant().collection() : "gsmv_rag_chunks";
    }

    private int dimension() {
        return properties.embedding().dimension() == null ? 1024 : properties.embedding().dimension();
    }

    public record QdrantPoint(Long id, List<Double> vector, Map<String, Object> payload) {
    }

    public record QdrantSearchHit(Long chunkId, double score) {
    }

    public record QdrantStatus(boolean available, String status, long pointsCount, String errorMessage) {
    }
}
