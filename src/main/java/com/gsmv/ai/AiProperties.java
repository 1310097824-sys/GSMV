package com.gsmv.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gsmv.ai")
public record AiProperties(
        Bailian bailian,
        Ollama ollama,
        DeepSeek deepseek,
        Embedding embedding,
        double lowConfidenceThreshold,
        int assistantObservationLimit,
        int assistantSpeciesLimit
) {

        public record Bailian(
            boolean enabled,
            String apiKey,
            String baseUrl,
            String visionModel,
            String embeddingModel,
            Integer embeddingDimension
    ) {
    }

    public record Ollama(
            Boolean enabled,
            String baseUrl,
            String embeddingModel,
            Integer embeddingDimension
    ) {
    }

    public record DeepSeek(
            boolean enabled,
            String apiKey,
            String baseUrl,
            String chatModel
    ) {
    }

    public record Embedding(
            String provider
    ) {
    }
}
