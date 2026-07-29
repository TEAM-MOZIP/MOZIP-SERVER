package com.mozip.server.policy.evaluator;

import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityCandidate;
import java.util.Comparator;

public class PolicyAvailabilityComparator {

    private static final Comparator<PolicyAvailabilityCandidate> INSTANCE = Comparator
            .comparingInt((PolicyAvailabilityCandidate candidate) -> availabilityPriority(candidate.availabilityResult().status()))
            .thenComparing(candidate -> candidate.policy().getApplicationEndDate(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(candidate -> candidate.policy().getCreatedAt(), Comparator.reverseOrder())
            .thenComparing(candidate -> candidate.policy().getId());

    private PolicyAvailabilityComparator() {
    }

    public static Comparator<PolicyAvailabilityCandidate> comparator() {
        return INSTANCE;
    }

    private static int availabilityPriority(PolicyAvailability status) {
        return switch (status) {
            case AVAILABLE -> 0;
            case NEEDS_REVIEW -> 1;
            case UNAVAILABLE -> 2;
        };
    }
}
