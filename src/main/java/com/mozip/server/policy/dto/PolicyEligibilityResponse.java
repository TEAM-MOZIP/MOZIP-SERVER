package com.mozip.server.policy.dto;

import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.user.entity.IncomeType;
import java.util.List;
import java.util.Map;

public record PolicyEligibilityResponse(
        Integer minimumAge,
        Integer maximumAge,
        String genderCondition,
        IncomeType incomeType,
        Integer minimumIncomeValue,
        Integer maximumIncomeValue,
        List<String> allowedEmploymentStatuses,
        List<String> allowedHouseholdTypes,
        Map<String, Object> additionalConditions
) {

    public static PolicyEligibilityResponse from(PolicyEligibility eligibility) {
        return new PolicyEligibilityResponse(
                eligibility.getMinimumAge(),
                eligibility.getMaximumAge(),
                eligibility.getGenderCondition(),
                eligibility.getIncomeType(),
                eligibility.getMinimumIncomeValue(),
                eligibility.getMaximumIncomeValue(),
                eligibility.getAllowedEmploymentStatuses(),
                eligibility.getAllowedHouseholdTypes(),
                eligibility.getAdditionalConditions()
        );
    }
}