package com.mozip.server.recommendation.dto;

import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import java.time.LocalDate;

public record PolicyRecommendationResponse(
        Long policyId,
        String title,
        String organizationName,
        ApplicationType applicationType,
        LocalDate applicationStartDate,
        LocalDate applicationEndDate,
        PolicyEvaluationResponse.EligibilityResponse eligibility,
        PolicyEvaluationResponse.AvailabilityResponse availability,
        boolean bookmarked,
        Double semanticScore
) {

    public static PolicyRecommendationResponse from(Policy policy, PolicyEligibilityResult eligibilityResult,
                                                      PolicyAvailabilityResult availabilityResult, boolean bookmarked,
                                                      Double semanticScore) {
        return new PolicyRecommendationResponse(
                policy.getId(),
                policy.getTitle(),
                policy.getOrganization().getName(),
                policy.getApplicationType(),
                policy.getApplicationStartDate(),
                policy.getApplicationEndDate(),
                PolicyEvaluationResponse.EligibilityResponse.from(eligibilityResult),
                PolicyEvaluationResponse.AvailabilityResponse.from(availabilityResult),
                bookmarked,
                semanticScore
        );
    }
}
