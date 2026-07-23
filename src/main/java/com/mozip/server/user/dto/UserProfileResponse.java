package com.mozip.server.user.dto;

import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;
import com.mozip.server.user.entity.UserProfile;
import java.time.LocalDate;

public record UserProfileResponse(
        Long userId,
        LocalDate birthDate,
        Long regionId,
        String regionName,
        String gender,
        IncomeType incomeType,
        Integer incomeValue,
        EmploymentStatus employmentStatus,
        HouseholdType householdType
) {

    public static UserProfileResponse from(UserProfile profile) {
        return new UserProfileResponse(
                profile.getUser().getId(),
                profile.getBirthDate(),
                profile.getRegion() != null ? profile.getRegion().getId() : null,
                profile.getRegion() != null ? profile.getRegion().getName() : null,
                profile.getGender(),
                profile.getIncomeType(),
                profile.getIncomeValue(),
                profile.getEmploymentStatus(),
                profile.getHouseholdType()
        );
    }
}
