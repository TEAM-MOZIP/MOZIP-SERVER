package com.mozip.server.policy.dto;

import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Category;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.region.dto.RegionResponse;
import com.mozip.server.region.entity.Region;
import java.time.LocalDate;
import java.util.List;

public record PolicySummaryResponse(
        Long id,
        String title,
        String summary,
        String organizationName,
        ApplicationType applicationType,
        LocalDate applicationStartDate,
        LocalDate applicationEndDate,
        RegionScope regionScope,
        PolicyStatus status,
        PolicyAvailabilityResponse availability,
        List<CategoryResponse> categories,
        List<RegionResponse> regions
) {

    /**
     * @param regions 정책에 연결된 지역(policy_regions). 전국 정책(regionScope=NATIONAL)은 비어 있다.
     */
    public PolicySummaryResponse {
        categories = categories != null ? List.copyOf(categories) : List.of();
        regions = regions != null ? List.copyOf(regions) : List.of();
    }

    /** 카테고리·지역 없이 만드는 기존 생성자(테스트 등 하위 호환용). */
    public PolicySummaryResponse(Long id, String title, String summary, String organizationName,
                                 ApplicationType applicationType, LocalDate applicationStartDate,
                                 LocalDate applicationEndDate, RegionScope regionScope, PolicyStatus status,
                                 PolicyAvailabilityResponse availability) {
        this(id, title, summary, organizationName, applicationType, applicationStartDate, applicationEndDate,
                regionScope, status, availability, List.of(), List.of());
    }

    public static PolicySummaryResponse from(Policy policy, PolicyAvailabilityResult availabilityResult,
                                             List<Category> categories, List<Region> regions) {
        return new PolicySummaryResponse(
                policy.getId(),
                policy.getTitle(),
                policy.getSummary(),
                policy.getOrganization().getName(),
                policy.getApplicationType(),
                policy.getApplicationStartDate(),
                policy.getApplicationEndDate(),
                policy.getRegionScope(),
                policy.getStatus(),
                PolicyAvailabilityResponse.from(availabilityResult),
                categories.stream().map(CategoryResponse::from).toList(),
                regions.stream().map(RegionResponse::from).toList()
        );
    }
}