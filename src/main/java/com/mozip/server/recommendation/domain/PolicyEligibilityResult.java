package com.mozip.server.recommendation.domain;

import java.util.List;

public record PolicyEligibilityResult(
        EligibilityStatus overallStatus,
        String overallReason,
        List<ConditionResult> conditionResults
) {

    private static final String ELIGIBLE_REASON = "모든 자동 판정 조건을 충족했습니다.";
    private static final String INELIGIBLE_REASON = "충족하지 못한 자격 조건이 있습니다.";
    private static final String NEEDS_REVIEW_REASON = "자동 판정할 수 없는 조건이 있습니다.";

    /**
     * 축별 판정 결과 목록에서 전체 상태를 도출한다. NOT_MATCHED가 하나라도 있으면 INELIGIBLE, 없고
     * NEEDS_REVIEW가 있으면 NEEDS_REVIEW, 전부 MATCHED면 ELIGIBLE — UserProfile 기반 평가와 챗봇
     * transient 조건 기반 평가가 동일한 규칙을 공유한다.
     */
    public static PolicyEligibilityResult summarize(List<ConditionResult> conditionResults) {
        boolean hasNotMatched = conditionResults.stream().anyMatch(r -> r.status() == ConditionStatus.NOT_MATCHED);
        boolean hasNeedsReview = conditionResults.stream().anyMatch(r -> r.status() == ConditionStatus.NEEDS_REVIEW);

        if (hasNotMatched) {
            return new PolicyEligibilityResult(EligibilityStatus.INELIGIBLE, INELIGIBLE_REASON, conditionResults);
        }
        if (hasNeedsReview) {
            return new PolicyEligibilityResult(EligibilityStatus.NEEDS_REVIEW, NEEDS_REVIEW_REASON, conditionResults);
        }
        return new PolicyEligibilityResult(EligibilityStatus.ELIGIBLE, ELIGIBLE_REASON, conditionResults);
    }
}
