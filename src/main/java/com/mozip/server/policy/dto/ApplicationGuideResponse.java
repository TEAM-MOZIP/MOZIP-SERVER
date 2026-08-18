package com.mozip.server.policy.dto;

import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyApplicationInfo;
import com.mozip.server.policy.entity.PolicyEligibility;
import java.time.LocalDate;
import java.util.List;

public record ApplicationGuideResponse(
        Long policyId,
        PolicyEligibilityResponse requirements,
        String requirementsDescription,
        ApplicationType applicationType,
        LocalDate applicationStartDate,
        LocalDate applicationEndDate,
        PolicyAvailabilityResponse availability,
        List<ApplicationGuideStepResponse> steps,
        List<String> requiredDocuments,
        String applicationUrl,
        String contactInfo,
        String notes
) {

    public static ApplicationGuideResponse from(Policy policy, PolicyEligibility eligibility,
                                                 PolicyAvailabilityResult availabilityResult,
                                                 PolicyApplicationInfo applicationInfo,
                                                 List<ApplicationGuideStepResponse> steps,
                                                 List<String> requiredDocuments) {
        return new ApplicationGuideResponse(
                policy.getId(),
                eligibility != null ? PolicyEligibilityResponse.from(eligibility) : null,
                policy.getTargetDescription(),
                policy.getApplicationType(),
                policy.getApplicationStartDate(),
                policy.getApplicationEndDate(),
                PolicyAvailabilityResponse.from(availabilityResult),
                steps,
                requiredDocuments,
                applicationInfo != null ? applicationInfo.getApplicationUrl() : null,
                applicationInfo != null ? applicationInfo.getContactInfo() : null,
                applicationInfo != null ? applicationInfo.getApplicationNotes() : null
        );
    }
}
