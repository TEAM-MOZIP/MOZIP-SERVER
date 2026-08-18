package com.mozip.server.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mozip.server.ai.client.RecommendationReasonClient;
import com.mozip.server.ai.dto.RecommendationReasonResponse;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.recommendation.domain.ConditionResult;
import com.mozip.server.recommendation.domain.ConditionStatus;
import com.mozip.server.recommendation.domain.ConditionType;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class PolicyRecommendationReasonServiceTest {

    private final RecommendationReasonClient recommendationReasonClient = mock(RecommendationReasonClient.class);
    private final PolicyRecommendationReasonService policyRecommendationReasonService =
            new PolicyRecommendationReasonService(recommendationReasonClient);

    @Test
    void AI_응답이_성공이면_overallReason이_교체된다() {
        PolicyEligibilityResult original = eligibilityResult("규칙 기반 사유");
        when(recommendationReasonClient.explain(any()))
                .thenReturn(new RecommendationReasonResponse("AI가 생성한 사유"));

        PolicyEligibilityResult result = policyRecommendationReasonService.enhance(policy(), original);

        assertThat(result.overallReason()).isEqualTo("AI가 생성한 사유");
        assertThat(result.overallStatus()).isEqualTo(original.overallStatus());
        assertThat(result.conditionResults()).isEqualTo(original.conditionResults());
    }

    @Test
    void AI_호출이_RestClientException을_던지면_원본을_그대로_반환한다() {
        PolicyEligibilityResult original = eligibilityResult("규칙 기반 사유");
        when(recommendationReasonClient.explain(any())).thenThrow(new ResourceAccessException("연결 실패"));

        PolicyEligibilityResult result = policyRecommendationReasonService.enhance(policy(), original);

        assertThat(result).isEqualTo(original);
    }

    @Test
    void 응답이_null이면_원본을_그대로_반환한다() {
        PolicyEligibilityResult original = eligibilityResult("규칙 기반 사유");
        when(recommendationReasonClient.explain(any())).thenReturn(null);

        PolicyEligibilityResult result = policyRecommendationReasonService.enhance(policy(), original);

        assertThat(result).isEqualTo(original);
    }

    @Test
    void reason이_null이면_원본을_그대로_반환한다() {
        PolicyEligibilityResult original = eligibilityResult("규칙 기반 사유");
        when(recommendationReasonClient.explain(any())).thenReturn(new RecommendationReasonResponse(null));

        PolicyEligibilityResult result = policyRecommendationReasonService.enhance(policy(), original);

        assertThat(result).isEqualTo(original);
    }

    @Test
    void reason이_공백문자열이면_원본을_그대로_반환한다() {
        PolicyEligibilityResult original = eligibilityResult("규칙 기반 사유");
        when(recommendationReasonClient.explain(any())).thenReturn(new RecommendationReasonResponse("   "));

        PolicyEligibilityResult result = policyRecommendationReasonService.enhance(policy(), original);

        assertThat(result).isEqualTo(original);
    }

    private PolicyEligibilityResult eligibilityResult(String overallReason) {
        ConditionResult conditionResult = new ConditionResult(ConditionType.AGE, ConditionStatus.MATCHED, "연령 조건을 충족합니다.");
        return new PolicyEligibilityResult(EligibilityStatus.ELIGIBLE, overallReason, List.of(conditionResult));
    }

    private Policy policy() {
        Organization organization = Organization.builder().name("테스트기관").build();
        return Policy.builder()
                .organization(organization)
                .title("테스트정책")
                .build();
    }
}
