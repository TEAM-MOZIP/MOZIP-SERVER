package com.mozip.server.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * @param chatTimeoutSeconds 챗봇 응답(/chat/respond) 전용 timeout. 긴 답변 생성에 시간이 걸려
 *                           다른 설명 계열 호출보다 길게 둔다. 비어 있으면 explainTimeoutSeconds를 쓴다.
 */
@ConfigurationProperties(prefix = "mozip.ai")
public record MozipAiProperties(
        String baseUrl,
        Integer timeoutSeconds,
        Integer explainTimeoutSeconds,
        Integer chatTimeoutSeconds
) {

    @ConstructorBinding
    public MozipAiProperties {
    }

    public MozipAiProperties(String baseUrl, Integer timeoutSeconds, Integer explainTimeoutSeconds) {
        this(baseUrl, timeoutSeconds, explainTimeoutSeconds, null);
    }

    public int resolvedChatTimeoutSeconds() {
        return chatTimeoutSeconds != null ? chatTimeoutSeconds : explainTimeoutSeconds;
    }
}
