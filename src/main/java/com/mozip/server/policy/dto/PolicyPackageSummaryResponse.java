package com.mozip.server.policy.dto;

/**
 * 패키지 카드용 요약. 제목·문구 등 표시 정보는 클라이언트가 {@code packageId}로 관리한다.
 *
 * @param policyCount 패키지에 들어간 정책 수(여러 섹션에 들어간 정책은 한 번만 센다)
 */
public record PolicyPackageSummaryResponse(
        String packageId,
        int policyCount
) {
}
