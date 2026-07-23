package com.mozip.server.user.dto;

import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public record UserProfileUpdateRequest(
        @NotNull @PastOrPresent LocalDate birthDate,
        @NotNull Long regionId,
        // TODO: 허용값이 확정되면 Enum + @NotNull로 전환 (현재는 자유 문자열 + 공백 검증만)
        @NotBlank String gender,
        @NotNull IncomeType incomeType,
        @NotNull @PositiveOrZero Integer incomeValue,
        @NotNull EmploymentStatus employmentStatus,
        @NotNull HouseholdType householdType
) {
}
