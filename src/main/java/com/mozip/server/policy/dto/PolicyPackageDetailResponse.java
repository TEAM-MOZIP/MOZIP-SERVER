package com.mozip.server.policy.dto;

import java.util.List;

/**
 * 패키지 상세. 섹션은 표시 순서대로 모두 담으며(빈 섹션 포함), 각 섹션은 미리보기 정책만 담는다.
 * 섹션 전체는 섹션 조회 API로 페이지 단위로 불러온다.
 */
public record PolicyPackageDetailResponse<T>(
        String packageId,
        int policyCount,
        List<PolicyPackageSectionResponse<T>> sections
) {
}
