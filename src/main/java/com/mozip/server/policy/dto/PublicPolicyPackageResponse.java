package com.mozip.server.policy.dto;

import com.mozip.server.policy.entity.Category;
import java.util.List;

public record PublicPolicyPackageResponse(
        Long categoryId,
        String categoryName,
        List<PolicySummaryResponse> policies
) {

    public static PublicPolicyPackageResponse from(Category category, List<PolicySummaryResponse> policies) {
        return new PublicPolicyPackageResponse(category.getId(), category.getName(), policies);
    }
}
