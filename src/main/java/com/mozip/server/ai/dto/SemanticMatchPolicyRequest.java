package com.mozip.server.ai.dto;

import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.user.entity.Gender;
import com.mozip.server.user.entity.IncomeType;
import java.util.List;

public record SemanticMatchPolicyRequest(
        Long policyId,
        RegionScope regionScope,
        List<String> regionCodes,
        Integer minimumAge,
        Integer maximumAge,
        Gender genderCondition,
        IncomeType incomeType,
        List<String> allowedEmploymentStatuses,
        List<String> allowedHouseholdTypes
) {
}
