package com.mozip.server.chat.dto;

import com.mozip.server.policy.entity.Policy;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;

public record ChatPolicyMatchResult(
        Policy policy,
        PolicyEligibilityResult eligibilityResult
) {
}
