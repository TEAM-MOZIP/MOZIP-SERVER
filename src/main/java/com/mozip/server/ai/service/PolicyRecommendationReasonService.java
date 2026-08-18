package com.mozip.server.ai.service;

import com.mozip.server.ai.client.RecommendationReasonClient;
import com.mozip.server.ai.dto.RecommendationReasonRequest;
import com.mozip.server.ai.dto.RecommendationReasonResponse;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class PolicyRecommendationReasonService {

    private static final Logger log = LoggerFactory.getLogger(PolicyRecommendationReasonService.class);

    private final RecommendationReasonClient recommendationReasonClient;

    public PolicyRecommendationReasonService(RecommendationReasonClient recommendationReasonClient) {
        this.recommendationReasonClient = recommendationReasonClient;
    }

    public PolicyEligibilityResult enhance(Policy policy, PolicyEligibilityResult eligibilityResult) {
        RecommendationReasonRequest request = RecommendationReasonRequest.from(policy, eligibilityResult);

        RecommendationReasonResponse response;
        try {
            response = recommendationReasonClient.explain(request);
        } catch (RestClientException e) {
            log.warn("추천 이유 생성 호출에 실패해 규칙 기반 overallReason을 유지합니다. exceptionType={}",
                    e.getClass().getSimpleName());
            return eligibilityResult;
        }

        if (response == null || response.reason() == null || response.reason().isBlank()) {
            log.warn("추천 이유 생성 응답이 비어 있어 규칙 기반 overallReason을 유지합니다.");
            return eligibilityResult;
        }

        return new PolicyEligibilityResult(eligibilityResult.overallStatus(), response.reason(),
                eligibilityResult.conditionResults());
    }
}
