package com.mozip.server.recommendation.domain;

import java.util.List;

public record PolicyEligibilityResult(
        EligibilityStatus overallStatus,
        String overallReason,
        List<ConditionResult> conditionResults
) {
}
