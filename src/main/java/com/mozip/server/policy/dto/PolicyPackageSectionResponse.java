package com.mozip.server.policy.dto;

import java.util.List;

/**
 * @param totalCount 섹션 전체 정책 수. {@code policies}는 미리보기로 앞부분만 담는다.
 * @param <T>        공개 패키지는 {@link PolicySummaryResponse}, 개인화 패키지는 PolicyRecommendationResponse
 */
public record PolicyPackageSectionResponse<T>(
        String sectionKey,
        String sectionName,
        int totalCount,
        List<T> policies
) {
}
