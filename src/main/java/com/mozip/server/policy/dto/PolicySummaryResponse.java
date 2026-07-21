package com.mozip.server.policy.dto;

import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import java.time.LocalDate;

public record PolicySummaryResponse(
        Long id,
        String title,
        String summary,
        String organizationName,
        ApplicationType applicationType,
        LocalDate applicationStartDate,
        LocalDate applicationEndDate,
        RegionScope regionScope,
        PolicyStatus status
) {

    public static PolicySummaryResponse from(Policy policy) {
        return new PolicySummaryResponse(
                policy.getId(),
                policy.getTitle(),
                policy.getSummary(),
                policy.getOrganization().getName(),
                policy.getApplicationType(),
                policy.getApplicationStartDate(),
                policy.getApplicationEndDate(),
                policy.getRegionScope(),
                policy.getStatus()
        );
    }
}