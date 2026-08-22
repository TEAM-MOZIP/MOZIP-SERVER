package com.mozip.server.ai.dto;

import java.util.List;

public record ChatResponseRequest(
        String message,
        List<GroundingPolicy> groundingPolicies,
        PolicyDetailGrounding policyDetail,
        List<UnresolvedCondition> unresolvedConditions
) {
}
