package com.mozip.server.ai.dto;

import com.mozip.server.policy.entity.Policy;
import com.mozip.server.recommendation.domain.ConditionResult;
import com.mozip.server.recommendation.domain.ConditionStatus;
import com.mozip.server.recommendation.domain.ConditionType;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import java.util.List;

public record RecommendationReasonRequest(
        String policyTitle,
        EligibilityStatus eligibilityStatus,
        List<ConditionEntry> conditions
) {

    public static RecommendationReasonRequest from(Policy policy, PolicyEligibilityResult eligibilityResult) {
        List<ConditionEntry> conditions = eligibilityResult.conditionResults().stream()
                .map(ConditionEntry::from)
                .toList();
        return new RecommendationReasonRequest(policy.getTitle(), eligibilityResult.overallStatus(), conditions);
    }

    public record ConditionEntry(
            ConditionType type,
            ConditionStatus status,
            String reason
    ) {

        public static ConditionEntry from(ConditionResult conditionResult) {
            return new ConditionEntry(conditionResult.type(), conditionResult.status(), conditionResult.reason());
        }
    }
}
