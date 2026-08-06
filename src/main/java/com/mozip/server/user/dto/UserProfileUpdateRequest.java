package com.mozip.server.user.dto;

import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.Gender;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public record UserProfileUpdateRequest(
        @NotNull @PastOrPresent LocalDate birthDate,
        @NotNull Long regionId,
        @NotNull Gender gender,
        @NotNull IncomeType incomeType,
        @NotNull @PositiveOrZero Integer incomeValue,
        @NotNull EmploymentStatus employmentStatus,
        @NotNull HouseholdType householdType
) {
}
