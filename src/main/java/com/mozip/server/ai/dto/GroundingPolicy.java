package com.mozip.server.ai.dto;

import com.mozip.server.recommendation.domain.EligibilityStatus;
import java.time.LocalDate;

public record GroundingPolicy(
        Long policyId,
        String title,
        EligibilityStatus eligibilityStatus,
        LocalDate applicationEndDate
) {
}
