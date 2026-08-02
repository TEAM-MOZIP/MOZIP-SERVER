package com.mozip.server.policy.domain;

public record PolicyAvailabilityResult(PolicyAvailability status, PolicyAvailabilityReason reason, boolean closingSoon) {
}
