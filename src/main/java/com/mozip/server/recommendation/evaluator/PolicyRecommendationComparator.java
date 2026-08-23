package com.mozip.server.recommendation.evaluator;

import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyRecommendationCandidate;
import java.util.Comparator;

/**
 * 정렬 우선순위: INELIGIBLE 여부 → availability → eligibility → semanticScore →
 * applicationEndDate → createdAt → id.
 *
 * <p>{@code INELIGIBLE}은 다른 어떤 요소와도 무관하게 항상 최하위다(불변, 변경 없음).
 * 그 외 후보 사이에서는 "지금 신청 가능한가"(availability)를 "자격 요건에 맞는가"
 * (eligibility)보다 먼저 비교한다 — 자격은 확실하지만 이미 마감된 정책보다, 자격은
 * 추가 확인이 필요하지만 지금 신청 가능한 정책이 사용자에게 더 실질적인 의미가 있다는
 * Phase E 판단에 따른 의도적 변경이다. 즉 기존 "ELIGIBLE이 NEEDS_REVIEW보다 항상
 * 우선한다"는 불변식은 같은 availability 안에서만 성립하도록 범위가 좁혀졌다
 * (예: NEEDS_REVIEW+AVAILABLE이 ELIGIBLE+UNAVAILABLE보다 우선).
 */
public class PolicyRecommendationComparator {

    private static final Comparator<PolicyRecommendationCandidate> INSTANCE = Comparator
            .comparingInt((PolicyRecommendationCandidate candidate) -> ineligiblePriority(candidate.eligibilityResult().overallStatus()))
            .thenComparingInt(candidate -> availabilityPriority(candidate.availabilityResult().status()))
            .thenComparingInt(candidate -> eligibilityPriority(candidate.eligibilityResult().overallStatus()))
            .thenComparing(PolicyRecommendationCandidate::semanticScore,
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(candidate -> candidate.policy().getApplicationEndDate(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(candidate -> candidate.policy().getCreatedAt(), Comparator.reverseOrder())
            .thenComparing(candidate -> candidate.policy().getId());

    private PolicyRecommendationComparator() {
    }

    public static Comparator<PolicyRecommendationCandidate> comparator() {
        return INSTANCE;
    }

    private static int ineligiblePriority(EligibilityStatus status) {
        return status == EligibilityStatus.INELIGIBLE ? 1 : 0;
    }

    private static int availabilityPriority(PolicyAvailability availability) {
        return switch (availability) {
            case AVAILABLE -> 0;
            case NEEDS_REVIEW -> 1;
            case UNAVAILABLE -> 2;
        };
    }

    private static int eligibilityPriority(EligibilityStatus status) {
        return switch (status) {
            case ELIGIBLE -> 0;
            case NEEDS_REVIEW -> 1;
            case INELIGIBLE -> 2;
        };
    }
}
