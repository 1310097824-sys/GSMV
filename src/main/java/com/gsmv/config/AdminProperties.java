package com.gsmv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gsmv.admin")
public record AdminProperties(
        boolean enabled,
        String username,
        String displayName,
        String bootstrapPassword
) {
}
