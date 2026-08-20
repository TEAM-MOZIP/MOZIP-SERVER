package com.mozip.server.ai.service;

import com.mozip.server.ai.client.PolicySummaryClient;
import com.mozip.server.ai.dto.PolicySummaryRequest;
import com.mozip.server.ai.dto.PolicySummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class PolicySummaryGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PolicySummaryGenerationService.class);

    private final PolicySummaryClient policySummaryClient;

    public PolicySummaryGenerationService(PolicySummaryClient policySummaryClient) {
        this.policySummaryClient = policySummaryClient;
    }

    public String generate(String title, String description, String targetDescription, String benefitDescription) {
        PolicySummaryRequest request = new PolicySummaryRequest(title, description, targetDescription, benefitDescription);

        PolicySummaryResponse response;
        try {
            response = policySummaryClient.generate(request);
        } catch (RestClientException e) {
            log.warn("정책 요약 생성 호출에 실패했습니다. exceptionType={}", e.getClass().getSimpleName());
            return null;
        }

        if (response == null) {
            log.warn("정책 요약 생성 응답이 비어 있습니다.");
            return null;
        }

        return response.summary();
    }
}
