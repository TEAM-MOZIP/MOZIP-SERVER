package com.mozip.server.policy.evaluator;

import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class PolicyAvailabilityEvaluator {

    private static final long CLOSING_SOON_THRESHOLD_DAYS = 3;

    private final Clock clock;

    public PolicyAvailabilityEvaluator(Clock clock) {
        this.clock = clock;
    }

    public PolicyAvailabilityResult evaluate(Policy policy) {
        PolicyStatus status = policy.getStatus();

        if (status == PolicyStatus.CLOSED) {
            return new PolicyAvailabilityResult(PolicyAvailability.UNAVAILABLE, PolicyAvailabilityReason.CLOSED, false);
        }
        if (status == PolicyStatus.DRAFT) {
            return new PolicyAvailabilityResult(PolicyAvailability.UNAVAILABLE, PolicyAvailabilityReason.DRAFT, false);
        }
        if (status == PolicyStatus.SUSPENDED) {
            return new PolicyAvailabilityResult(PolicyAvailability.UNAVAILABLE, PolicyAvailabilityReason.SUSPENDED, false);
        }
        if (status == PolicyStatus.ALWAYS_OPEN) {
            return new PolicyAvailabilityResult(PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.ALWAYS_OPEN, false);
        }

        return evaluateOpenStatus(policy);
    }

    private PolicyAvailabilityResult evaluateOpenStatus(Policy policy) {
        ApplicationType applicationType = policy.getApplicationType();

        if (applicationType == ApplicationType.ALWAYS) {
            return new PolicyAvailabilityResult(PolicyAvailability.AVAILABLE,
                    PolicyAvailabilityReason.ALWAYS_APPLICATION_TYPE, false);
        }
        if (applicationType == ApplicationType.UNKNOWN) {
            return new PolicyAvailabilityResult(PolicyAvailability.NEEDS_REVIEW,
                    PolicyAvailabilityReason.UNKNOWN_APPLICATION_TYPE, false);
        }

        return evaluateApplicationPeriod(policy);
    }

    private PolicyAvailabilityResult evaluateApplicationPeriod(Policy policy) {
        LocalDate startDate = policy.getApplicationStartDate();
        LocalDate endDate = policy.getApplicationEndDate();

        if (startDate == null || endDate == null) {
            return new PolicyAvailabilityResult(PolicyAvailability.NEEDS_REVIEW,
                    PolicyAvailabilityReason.MISSING_APPLICATION_PERIOD, false);
        }
        if (startDate.isAfter(endDate)) {
            return new PolicyAvailabilityResult(PolicyAvailability.NEEDS_REVIEW,
                    PolicyAvailabilityReason.INVALID_APPLICATION_PERIOD, false);
        }

        LocalDate today = LocalDate.now(clock);
        if (today.isBefore(startDate)) {
            return new PolicyAvailabilityResult(PolicyAvailability.UNAVAILABLE,
                    PolicyAvailabilityReason.BEFORE_APPLICATION_PERIOD, false);
        }
        if (today.isAfter(endDate)) {
            return new PolicyAvailabilityResult(PolicyAvailability.UNAVAILABLE,
                    PolicyAvailabilityReason.AFTER_APPLICATION_PERIOD, false);
        }

        boolean closingSoon = ChronoUnit.DAYS.between(today, endDate) <= CLOSING_SOON_THRESHOLD_DAYS;
        return new PolicyAvailabilityResult(PolicyAvailability.AVAILABLE,
                PolicyAvailabilityReason.WITHIN_APPLICATION_PERIOD, closingSoon);
    }
}
