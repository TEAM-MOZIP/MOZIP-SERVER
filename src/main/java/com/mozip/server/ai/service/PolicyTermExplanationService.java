package com.mozip.server.ai.service;

import com.mozip.server.ai.client.TermExplanationClient;
import com.mozip.server.ai.dto.TermExplainRequest;
import com.mozip.server.ai.dto.TermExplainResponse;
import com.mozip.server.ai.exception.TermExplanationUnavailableException;
import com.mozip.server.policy.dto.TermExplanationRequest;
import com.mozip.server.policy.dto.TermExplanationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class PolicyTermExplanationService {

    private static final Logger log = LoggerFactory.getLogger(PolicyTermExplanationService.class);

    private final TermExplanationClient termExplanationClient;

    public PolicyTermExplanationService(TermExplanationClient termExplanationClient) {
        this.termExplanationClient = termExplanationClient;
    }

    public TermExplanationResponse explain(TermExplanationRequest request) {
        TermExplainRequest aiRequest = TermExplainRequest.from(request);

        TermExplainResponse response;
        try {
            response = termExplanationClient.explain(aiRequest);
        } catch (RestClientException e) {
            log.warn("용어 설명 생성 호출에 실패했습니다. exceptionType={}", e.getClass().getSimpleName());
            throw new TermExplanationUnavailableException();
        }

        if (response == null || response.explanation() == null || response.explanation().isBlank()) {
            log.warn("용어 설명 생성 응답이 비어 있습니다.");
            throw new TermExplanationUnavailableException();
        }

        return new TermExplanationResponse(response.explanation());
    }
}
