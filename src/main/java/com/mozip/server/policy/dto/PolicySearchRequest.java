package com.mozip.server.policy.dto;

import com.mozip.server.policy.entity.PolicyStatus;

public record PolicySearchRequest(
        String keyword,
        Long categoryId,
        Long regionId,
        PolicyStatus status
) {
}
