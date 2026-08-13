package com.mozip.server.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mozip.ai")
public record MozipAiProperties(
        String baseUrl,
        Integer timeoutSeconds
) {
}
