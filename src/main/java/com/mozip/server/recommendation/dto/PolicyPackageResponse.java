package com.mozip.server.recommendation.dto;

import com.mozip.server.policy.entity.Category;
import java.util.List;

public record PolicyPackageResponse(
        Long categoryId,
        String categoryName,
        List<PolicyRecommendationResponse> policies
) {

    public static PolicyPackageResponse from(Category category, List<PolicyRecommendationResponse> policies) {
        return new PolicyPackageResponse(category.getId(), category.getName(), policies);
    }
}
