package com.mozip.server.bookmark.dto;

import com.mozip.server.bookmark.entity.Bookmark;
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
        LocalDateTime bookmarkedAt
) {

    public static BookmarkResponse from(Bookmark bookmark) {
        Policy policy = bookmark.getPolicy();
        return new BookmarkResponse(
                bookmark.getId(),
                policy.getId(),
                policy.getTitle(),
                policy.getOrganization().getName(),
                policy.getApplicationStartDate(),
                policy.getApplicationEndDate(),
                bookmark.getCreatedAt()
        );
    }
}
