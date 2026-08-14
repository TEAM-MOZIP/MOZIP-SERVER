package com.mozip.server.recommendation.evaluator;

import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyRecommendationCandidate;
import java.util.Comparator;

public class PolicyRecommendationComparator {

    private static final Comparator<PolicyRecommendationCandidate> INSTANCE = Comparator
            .comparingInt((PolicyRecommendationCandidate candidate) -> statusPriority(candidate.eligibilityResult().overallStatus()))
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

    private static int statusPriority(EligibilityStatus status) {
        return switch (status) {
            case ELIGIBLE -> 0;
            case NEEDS_REVIEW -> 1;
            case INELIGIBLE -> 2;
        };
    }
}
