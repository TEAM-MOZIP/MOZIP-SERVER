package com.mozip.server.ai.dto;

import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.Gender;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;
import java.time.LocalDate;

public record SemanticMatchUserRequest(
        Gender gender,
        LocalDate birthDate,
        String regionCode,
        EmploymentStatus employmentStatus,
        HouseholdType householdType,
        IncomeType incomeType
) {
}
