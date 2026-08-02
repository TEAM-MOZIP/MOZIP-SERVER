package com.mozip.server.bookmark.dto;

import com.mozip.server.bookmark.entity.Bookmark;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.dto.PolicyAvailabilityResponse;
import com.mozip.server.policy.entity.Policy;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BookmarkResponse(
        Long bookmarkId,
        Long policyId,
        String title,
        String organizationName,
        LocalDate applicationStartDate,
        LocalDate applicationEndDate,
        PolicyAvailabilityResponse availability,
        LocalDateTime bookmarkedAt
) {

    public static BookmarkResponse from(Bookmark bookmark, PolicyAvailabilityResult availabilityResult) {
        Policy policy = bookmark.getPolicy();
        return new BookmarkResponse(
                bookmark.getId(),
                policy.getId(),
                policy.getTitle(),
                policy.getOrganization().getName(),
                policy.getApplicationStartDate(),
                policy.getApplicationEndDate(),
                PolicyAvailabilityResponse.from(availabilityResult),
                bookmark.getCreatedAt()
        );
    }
}
