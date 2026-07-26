package com.mozip.server.policy.dto;

import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;

public record PolicyAvailabilityResponse(
        PolicyAvailability status,
        PolicyAvailabilityReason reason
) {

    public static PolicyAvailabilityResponse from(PolicyAvailabilityResult result) {
        return new PolicyAvailabilityResponse(result.status(), result.reason());
    }
}
