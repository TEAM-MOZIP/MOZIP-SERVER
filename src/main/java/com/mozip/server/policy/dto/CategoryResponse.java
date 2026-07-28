package com.mozip.server.policy.dto;

import com.mozip.server.policy.entity.Category;

public record CategoryResponse(
        Long id,
        String code,
        String name
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getCode(), category.getName());
    }
}
