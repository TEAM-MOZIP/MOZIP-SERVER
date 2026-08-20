package com.mozip.server.policy.dto;

public record PolicySummaryContentResponse(
        Long policyId,
        String summary
) {

    public static PolicySummaryContentResponse of(Long policyId, String summary) {
        return new PolicySummaryContentResponse(policyId, summary);
    }
}
