package com.gsmv.ai.rag;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "gsmv.rag")
public record RagProperties(
        Vector vector,
        Qdrant qdrant,
        Embedding embedding,
        Ingest ingest,
        Iucn iucn
) {
    public RagProperties {
        vector = vector == null ? new Vector("qdrant") : vector;
        qdrant = qdrant == null ? new Qdrant(true, "http://localhost:6333", "gsmv_rag_chunks") : qdrant;
        embedding = embedding == null ? new Embedding("bge-m3", 1024) : embedding;
        ingest = ingest == null ? new Ingest(DataSize.ofMegabytes(50), List.of()) : ingest;
        iucn = iucn == null ? new Iucn("https://api.iucnredlist.org/api/v4", "") : iucn;
    }

    public record Vector(String provider) {
    }

    public record Qdrant(Boolean enabled, String url, String collection) {
    }

    public record Embedding(String model, Integer dimension) {
    }

    public record Ingest(DataSize maxFileSize, List<String> allowedDomains) {
    }

    public record Iucn(String baseUrl, String apiToken) {
    }
}
