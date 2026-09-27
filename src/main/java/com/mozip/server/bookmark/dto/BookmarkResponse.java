package com.mozip.server.bookmark.dto;

import com.mozip.server.bookmark.entity.Bookmark;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.dto.PolicyAvailabilityResponse;
import com.mozip.server.policy.dto.CategoryResponse;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Category;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.region.dto.RegionResponse;
import com.mozip.server.region.entity.Region;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record BookmarkResponse(
        Long bookmarkId,
        Long policyId,
        String title,
        String organizationName,
        LocalDate applicationStartDate,
        LocalDate applicationEndDate,
        PolicyAvailabilityResponse availability,
        LocalDateTime bookmarkedAt,
        ApplicationType applicationType,
        List<CategoryResponse> categories,
        RegionScope regionScope,
        List<RegionResponse> regions,
        Integer minimumAge,
        Integer maximumAge
) {

    /**
     * 마이페이지 북마크 카드도 정책 목록 카드와 같은 칩(카테고리·지역·연령)을 보여줄 수 있도록
     * 신청 유형·카테고리·지역·대상 나이 범위를 함께 내려준다. 전국 정책은 regions가 비어 있다.
     */
    public BookmarkResponse {
        categories = categories != null ? List.copyOf(categories) : List.of();
        regions = regions != null ? List.copyOf(regions) : List.of();
    }

    /** 칩 정보 없이 만드는 기존 생성자(테스트 등 하위 호환용). */
    public BookmarkResponse(Long bookmarkId, Long policyId, String title, String organizationName,
                            LocalDate applicationStartDate, LocalDate applicationEndDate,
                            PolicyAvailabilityResponse availability, LocalDateTime bookmarkedAt) {
        this(bookmarkId, policyId, title, organizationName, applicationStartDate, applicationEndDate, availability,
                bookmarkedAt, null, List.of(), null, List.of(), null, null);
    }

    public static BookmarkResponse from(Bookmark bookmark, PolicyAvailabilityResult availabilityResult) {
        return from(bookmark, availabilityResult, List.of(), List.of(), null);
    }

    /** @param eligibility 정책 자격 조건(나이 범위 표시용). 없으면 null. */
    public static BookmarkResponse from(Bookmark bookmark, PolicyAvailabilityResult availabilityResult,
                                        List<Category> categories, List<Region> regions,
                                        PolicyEligibility eligibility) {
        Policy policy = bookmark.getPolicy();
        return new BookmarkResponse(
                bookmark.getId(),
                policy.getId(),
                policy.getTitle(),
                policy.getOrganization().getName(),
                policy.getApplicationStartDate(),
                policy.getApplicationEndDate(),
                PolicyAvailabilityResponse.from(availabilityResult),
                bookmark.getCreatedAt(),
                policy.getApplicationType(),
                categories.stream().map(CategoryResponse::from).toList(),
                policy.getRegionScope(),
                regions.stream().map(RegionResponse::from).toList(),
                eligibility != null ? eligibility.getMinimumAge() : null,
                eligibility != null ? eligibility.getMaximumAge() : null
        );
    }
}
