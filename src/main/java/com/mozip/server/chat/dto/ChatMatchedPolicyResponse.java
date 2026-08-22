package com.mozip.server.chat.dto;

import com.mozip.server.recommendation.domain.EligibilityStatus;

public record ChatMatchedPolicyResponse(
        Long policyId,
        String title,
        EligibilityStatus eligibilityStatus
) {

    public static ChatMatchedPolicyResponse from(ChatPolicyMatchResult match) {
        return new ChatMatchedPolicyResponse(
                match.policy().getId(),
                match.policy().getTitle(),
                match.eligibilityResult().overallStatus()
        );
    }
}
