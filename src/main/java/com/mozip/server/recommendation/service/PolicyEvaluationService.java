package com.mozip.server.recommendation.service;

import com.mozip.server.ai.service.PolicyRecommendationReasonService;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.evaluator.PolicyAvailabilityEvaluator;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import com.mozip.server.recommendation.dto.PolicyEvaluationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PolicyEvaluationService {

    private final PolicyEligibilityService policyEligibilityService;
    private final PolicyRepository policyRepository;
    private final PolicyAvailabilityEvaluator policyAvailabilityEvaluator;
    private final PolicyRecommendationReasonService policyRecommendationReasonService;

    public PolicyEvaluationService(PolicyEligibilityService policyEligibilityService,
                                    PolicyRepository policyRepository,
                                    PolicyAvailabilityEvaluator policyAvailabilityEvaluator,
                                    PolicyRecommendationReasonService policyRecommendationReasonService) {
        this.policyEligibilityService = policyEligibilityService;
        this.policyRepository = policyRepository;
        this.policyAvailabilityEvaluator = policyAvailabilityEvaluator;
        this.policyRecommendationReasonService = policyRecommendationReasonService;
    }

    public PolicyEvaluationResponse evaluate(Long userId, Long policyId) {
        PolicyEligibilityResult eligibilityResult = policyEligibilityService.evaluate(userId, policyId);

        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException(policyId));
        eligibilityResult = policyRecommendationReasonService.enhance(policy, eligibilityResult);

        PolicyAvailabilityResult availabilityResult = policyAvailabilityEvaluator.evaluate(policy);

        return PolicyEvaluationResponse.from(policyId, eligibilityResult, availabilityResult);
    }
}
