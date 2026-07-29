package com.mozip.server.policy.domain;

import com.mozip.server.policy.entity.Policy;

public record PolicyAvailabilityCandidate(
        Policy policy,
        PolicyAvailabilityResult availabilityResult
) {
}
