package com.gsmv.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gsmv.ai")
public record AiProperties(
        Bailian bailian,
        DeepSeek deepseek,
        double lowConfidenceThreshold,
        int assistantObservationLimit,
        int assistantSpeciesLimit
) {

    public record Bailian(
            boolean enabled,
            String apiKey,
            String baseUrl,
            String visionModel
    ) {
    }

    public record DeepSeek(
            boolean enabled,
            String apiKey,
            String baseUrl,
            String chatModel
    ) {
    }
}
