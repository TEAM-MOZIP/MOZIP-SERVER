package com.mozip.server.ai.dto;

import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.Gender;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;
import java.util.List;

public record ConditionExtractionResponse(
        Gender gender,
        Integer age,
        String regionCode,
        EmploymentStatus employmentStatus,
        HouseholdType householdType,
        IncomeType incomeType,
        Integer incomeValue,
        List<UnresolvedCondition> unresolvedConditions
) {
}
