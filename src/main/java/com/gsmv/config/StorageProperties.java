package com.gsmv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gsmv.storage")
public record StorageProperties(String uploadDir) {
}
