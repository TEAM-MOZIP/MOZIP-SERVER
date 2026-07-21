package com.mozip.server.policy.dto;

import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import java.time.LocalDate;

public record PolicyDetailResponse(
        Long id,
        String title,
        String summary,
        String description,
        String targetDescription,
        String benefitDescription,
        String applicationMethod,
        ApplicationType applicationType,
        LocalDate applicationStartDate,
        LocalDate applicationEndDate,
        RegionScope regionScope,
        PolicyStatus status,
        String sourceUrl,
        String organizationName,
        PolicyEligibilityResponse eligibility
) {

    public static PolicyDetailResponse from(Policy policy, PolicyEligibility eligibility) {
        return new PolicyDetailResponse(
                policy.getId(),
                policy.getTitle(),
                policy.getSummary(),
                policy.getDescription(),
                policy.getTargetDescription(),
                policy.getBenefitDescription(),
                policy.getApplicationMethod(),
                policy.getApplicationType(),
                policy.getApplicationStartDate(),
                policy.getApplicationEndDate(),
                policy.getRegionScope(),
                policy.getStatus(),
                policy.getSourceUrl(),
                policy.getOrganization().getName(),
                eligibility != null ? PolicyEligibilityResponse.from(eligibility) : null
        );
    }
}