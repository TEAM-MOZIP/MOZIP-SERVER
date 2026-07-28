package com.mozip.server.recommendation.domain;

import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.entity.Policy;

public record PolicyRecommendationCandidate(
        Policy policy,
        PolicyEligibilityResult eligibilityResult,
        PolicyAvailabilityResult availabilityResult
) {
}
