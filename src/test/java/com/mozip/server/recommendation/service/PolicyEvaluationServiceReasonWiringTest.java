package com.mozip.server.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mozip.server.ai.service.PolicyRecommendationReasonService;
import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.evaluator.PolicyAvailabilityEvaluator;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.recommendation.domain.ConditionResult;
import com.mozip.server.recommendation.domain.ConditionStatus;
import com.mozip.server.recommendation.domain.ConditionType;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import com.mozip.server.recommendation.dto.PolicyEvaluationResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PolicyEvaluationServiceReasonWiringTest {

    private final PolicyEligibilityService policyEligibilityService = mock(PolicyEligibilityService.class);
    private final PolicyRepository policyRepository = mock(PolicyRepository.class);
    private final PolicyAvailabilityEvaluator policyAvailabilityEvaluator = mock(PolicyAvailabilityEvaluator.class);
    private final PolicyRecommendationReasonService policyRecommendationReasonService =
            mock(PolicyRecommendationReasonService.class);

    private final PolicyEvaluationService policyEvaluationService = new PolicyEvaluationService(
            policyEligibilityService, policyRepository, policyAvailabilityEvaluator,
            policyRecommendationReasonService);

    @Test
    void eligibility_계산_후_policyRecommendationReasonService가_호출된다() {
        Policy policy = policy(1L);
        PolicyEligibilityResult ruleBasedResult = eligibilityResult("규칙 기반 사유");
        when(policyEligibilityService.evaluate(10L, 1L)).thenReturn(ruleBasedResult);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyRecommendationReasonService.enhance(policy, ruleBasedResult)).thenReturn(ruleBasedResult);
        when(policyAvailabilityEvaluator.evaluate(policy)).thenReturn(availabilityResult());

        policyEvaluationService.evaluate(10L, 1L);

        verify(policyRecommendationReasonService).enhance(policy, ruleBasedResult);
    }

    @Test
    void AI가_생성한_reason이_최종_overallReason으로_흘러가고_status와_conditionResults는_원본_그대로_보존된다() {
        Policy policy = policy(1L);
        PolicyEligibilityResult ruleBasedResult = eligibilityResult("규칙 기반 사유");
        PolicyEligibilityResult enhancedResult = new PolicyEligibilityResult(
                ruleBasedResult.overallStatus(), "AI가 생성한 사유", ruleBasedResult.conditionResults());
        when(policyEligibilityService.evaluate(10L, 1L)).thenReturn(ruleBasedResult);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policyRecommendationReasonService.enhance(policy, ruleBasedResult)).thenReturn(enhancedResult);
        when(policyAvailabilityEvaluator.evaluate(policy)).thenReturn(availabilityResult());

        PolicyEvaluationResponse response = policyEvaluationService.evaluate(10L, 1L);

        assertThat(response.eligibility().overallReason()).isEqualTo("AI가 생성한 사유");
        assertThat(response.eligibility().status()).isEqualTo(ruleBasedResult.overallStatus());
        assertThat(response.eligibility().conditionResults())
                .containsExactlyElementsOf(ruleBasedResult.conditionResults());
    }

    private PolicyEligibilityResult eligibilityResult(String overallReason) {
        List<ConditionResult> conditionResults = List.of(
                new ConditionResult(ConditionType.AGE, ConditionStatus.MATCHED, "연령 조건을 충족합니다."),
                new ConditionResult(ConditionType.INCOME, ConditionStatus.NEEDS_REVIEW, "소득 정보가 없어 자동 판정할 수 없음"),
                new ConditionResult(ConditionType.REGION, ConditionStatus.NOT_MATCHED, "지역 조건과 일치하지 않습니다."));
        return new PolicyEligibilityResult(EligibilityStatus.ELIGIBLE, overallReason, conditionResults);
    }

    private PolicyAvailabilityResult availabilityResult() {
        return new PolicyAvailabilityResult(PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.WITHIN_APPLICATION_PERIOD,
                false);
    }

    private Policy policy(Long id) {
        Organization organization = Organization.builder().name("테스트기관").build();
        Policy policy = Policy.builder()
                .organization(organization)
                .title("테스트정책")
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }
}
