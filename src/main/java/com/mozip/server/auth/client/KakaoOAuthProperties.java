package com.mozip.server.auth.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.oauth")
public record KakaoOAuthProperties(
        String clientId,
        String redirectUri
) {
}
