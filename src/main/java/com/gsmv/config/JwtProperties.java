package com.gsmv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gsmv.security.jwt")
public record JwtProperties(
        String issuer,
        String secret,
        long accessTokenTtlMinutes
) {
}
