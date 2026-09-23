package com.mozip.server.policy.dto;

import com.mozip.server.policy.domain.AgeGroup;
import com.mozip.server.policy.domain.AvailabilityFilter;
import com.mozip.server.policy.entity.PolicyStatus;

public record PolicySearchRequest(
        String keyword,
        Long categoryId,
        Long regionId,
        PolicyStatus status,
        AgeGroup ageGroup,
        AvailabilityFilter availability
) {

    /** 상태(availability) 필터 없이 만드는 기존 생성자(하위 호환용). */
    public PolicySearchRequest(String keyword, Long categoryId, Long regionId, PolicyStatus status,
                               AgeGroup ageGroup) {
        this(keyword, categoryId, regionId, status, ageGroup, null);
    }
}
