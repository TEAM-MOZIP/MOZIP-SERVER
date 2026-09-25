package com.mozip.server.policy.dto;

import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Category;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
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
        List<RegionResponse> regions,
        Integer minimumAge,
        Integer maximumAge,
        Boolean bookmarked
) {

    /**
     * @param regions 정책에 연결된 지역(policy_regions). 전국 정책(regionScope=NATIONAL)은 비어 있다.
     * @param minimumAge·maximumAge 정책 대상 나이 범위(만 나이). 제한이 없거나 알 수 없으면 null이다.
     * @param bookmarked 로그인 사용자의 북마크 여부. 비로그인 조회면 null이다.
     */
    public PolicySummaryResponse {
        categories = categories != null ? List.copyOf(categories) : List.of();
        regions = regions != null ? List.copyOf(regions) : List.of();
    }

    /** 나이 범위·북마크 여부 없이 만드는 생성자(하위 호환용). */
    public PolicySummaryResponse(Long id, String title, String summary, String organizationName,
                                 ApplicationType applicationType, LocalDate applicationStartDate,
                                 LocalDate applicationEndDate, RegionScope regionScope, PolicyStatus status,
                                 PolicyAvailabilityResponse availability, List<CategoryResponse> categories,
                                 List<RegionResponse> regions) {
        this(id, title, summary, organizationName, applicationType, applicationStartDate, applicationEndDate,
                regionScope, status, availability, categories, regions, null, null, null);
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
        return from(policy, availabilityResult, categories, regions, null);
    }

    /** @param eligibility 정책 자격 조건(나이 범위 표시용). 없으면 null. */
    public static PolicySummaryResponse from(Policy policy, PolicyAvailabilityResult availabilityResult,
                                             List<Category> categories, List<Region> regions,
                                             PolicyEligibility eligibility) {
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
                regions.stream().map(RegionResponse::from).toList(),
                eligibility != null ? eligibility.getMinimumAge() : null,
                eligibility != null ? eligibility.getMaximumAge() : null,
                null
        );
    }

    public PolicySummaryResponse withBookmarked(boolean bookmarked) {
        return new PolicySummaryResponse(id, title, summary, organizationName, applicationType, applicationStartDate,
                applicationEndDate, regionScope, status, availability, categories, regions, minimumAge, maximumAge,
                bookmarked);
    }
}