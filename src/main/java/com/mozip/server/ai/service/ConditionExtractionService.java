package com.mozip.server.ai.service;

import com.mozip.server.ai.client.ConditionExtractionClient;
import com.mozip.server.ai.dto.ConditionExtractionRequest;
import com.mozip.server.ai.dto.ConditionExtractionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * 조건 추출은 보조 capability다 — 실패해도 요청을 끊지 않고 null을 반환해, 호출자가
 * "actionable axis 없음"으로 간주해 일반 질문/정책 검색 경로로 진행할 수 있게 한다
 * (B-1/B-4 계열의 실패 흡수 패턴).
 */
@Component
public class ConditionExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ConditionExtractionService.class);

    private final ConditionExtractionClient conditionExtractionClient;

    public ConditionExtractionService(ConditionExtractionClient conditionExtractionClient) {
        this.conditionExtractionClient = conditionExtractionClient;
    }

    public ConditionExtractionResponse extract(String message) {
        try {
            return conditionExtractionClient.extract(new ConditionExtractionRequest(message));
        } catch (RestClientException e) {
            log.warn("조건 추출 호출에 실패해 actionable axis 없음으로 간주합니다. exceptionType={}",
                    e.getClass().getSimpleName());
            return null;
        }
    }
}
