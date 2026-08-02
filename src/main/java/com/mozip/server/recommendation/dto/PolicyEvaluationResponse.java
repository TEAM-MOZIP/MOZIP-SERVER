package com.mozip.server.recommendation.dto;

import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.recommendation.domain.ConditionResult;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import java.util.List;

public record PolicyEvaluationResponse(
        Long policyId,
        EligibilityResponse eligibility,
        AvailabilityResponse availability
) {

    public static PolicyEvaluationResponse from(Long policyId, PolicyEligibilityResult eligibilityResult,
                                                 PolicyAvailabilityResult availabilityResult) {
        return new PolicyEvaluationResponse(
                policyId,
                EligibilityResponse.from(eligibilityResult),
                AvailabilityResponse.from(availabilityResult)
        );
    }

    public record EligibilityResponse(
            EligibilityStatus status,
            String overallReason,
            List<ConditionResult> conditionResults
    ) {

        public static EligibilityResponse from(PolicyEligibilityResult result) {
            return new EligibilityResponse(result.overallStatus(), result.overallReason(), result.conditionResults());
        }
    }

    public record AvailabilityResponse(
            PolicyAvailability status,
            PolicyAvailabilityReason reason,
            boolean closingSoon
    ) {

        public static AvailabilityResponse from(PolicyAvailabilityResult result) {
            return new AvailabilityResponse(result.status(), result.reason(), result.closingSoon());
        }
    }
}
