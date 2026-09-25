package com.mozip.server.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * AI 서버 호출 설정. 기능별 timeout이 비어 있으면 explainTimeoutSeconds를 쓴다.
 *
 * @param chatTimeoutSeconds    챗봇 응답(/chat/respond). 긴 답변 생성에 시간이 걸려 가장 길게 둔다.
 * @param guideTimeoutSeconds   신청 가이드(/guides/generate). 여러 단계를 구조화해 생성한다.
 * @param summaryTimeoutSeconds 정책 요약(/summaries/generate). 정책별로 저장해 처음 한 번만 호출한다.
 * @param reasonTimeoutSeconds  추천 이유(/recommendations/explain).
 * @param termTimeoutSeconds    용어 설명(/terms/explain).
 */
@ConfigurationProperties(prefix = "mozip.ai")
public record MozipAiProperties(
        String baseUrl,
        Integer timeoutSeconds,
        Integer explainTimeoutSeconds,
        Integer chatTimeoutSeconds,
        Integer guideTimeoutSeconds,
        Integer summaryTimeoutSeconds,
        Integer reasonTimeoutSeconds,
        Integer termTimeoutSeconds
) {

    @ConstructorBinding
    public MozipAiProperties {
    }

    public MozipAiProperties(String baseUrl, Integer timeoutSeconds, Integer explainTimeoutSeconds) {
        this(baseUrl, timeoutSeconds, explainTimeoutSeconds, null, null, null, null, null);
    }

    public int resolvedChatTimeoutSeconds() {
        return orExplainTimeout(chatTimeoutSeconds);
    }

    public int resolvedGuideTimeoutSeconds() {
        return orExplainTimeout(guideTimeoutSeconds);
    }

    public int resolvedSummaryTimeoutSeconds() {
        return orExplainTimeout(summaryTimeoutSeconds);
    }

    public int resolvedReasonTimeoutSeconds() {
        return orExplainTimeout(reasonTimeoutSeconds);
    }

    public int resolvedTermTimeoutSeconds() {
        return orExplainTimeout(termTimeoutSeconds);
    }

    private int orExplainTimeout(Integer timeoutSeconds) {
        return timeoutSeconds != null ? timeoutSeconds : explainTimeoutSeconds;
    }
}
