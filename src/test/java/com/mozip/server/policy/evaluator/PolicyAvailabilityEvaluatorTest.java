package com.mozip.server.policy.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class PolicyAvailabilityEvaluatorTest {

    // 평가 기준일: 2026-06-15 로 고정
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final PolicyAvailabilityEvaluator evaluator = new PolicyAvailabilityEvaluator(clock);

    @Test
    void CLOSED이면_날짜와_무관하게_UNAVAILABLE이다() {
        Policy policy = policy(PolicyStatus.CLOSED, ApplicationType.PERIOD,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        PolicyAvailabilityResult result = evaluator.evaluate(policy);

        assertThat(result.status()).isEqualTo(PolicyAvailability.UNAVAILABLE);
        assertThat(result.reason()).isEqualTo(PolicyAvailabilityReason.CLOSED);
    }

    @Test
    void DRAFT이면_날짜와_무관하게_UNAVAILABLE이다() {
        Policy policy = policy(PolicyStatus.DRAFT, ApplicationType.PERIOD,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        PolicyAvailabilityResult result = evaluator.evaluate(policy);

        assertThat(result.status()).isEqualTo(PolicyAvailability.UNAVAILABLE);
        assertThat(result.reason()).isEqualTo(PolicyAvailabilityReason.DRAFT);
    }

    @Test
    void SUSPENDED이면_날짜와_무관하게_UNAVAILABLE이다() {
        Policy policy = policy(PolicyStatus.SUSPENDED, ApplicationType.PERIOD,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        PolicyAvailabilityResult result = evaluator.evaluate(policy);

        assertThat(result.status()).isEqualTo(PolicyAvailability.UNAVAILABLE);
        assertThat(result.reason()).isEqualTo(PolicyAvailabilityReason.SUSPENDED);
    }

    @Test
    void ALWAYS_OPEN이면_applicationType과_무관하게_AVAILABLE이다() {
        Policy policy = policy(PolicyStatus.ALWAYS_OPEN, ApplicationType.PERIOD,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31));

        PolicyAvailabilityResult result = evaluator.evaluate(policy);

        assertThat(result.status()).isEqualTo(PolicyAvailability.AVAILABLE);
        assertThat(result.reason()).isEqualTo(PolicyAvailabilityReason.ALWAYS_OPEN);
    }

    @Test
    void OPEN이고_ALWAYS면_날짜와_무관하게_AVAILABLE이다() {
        Policy withoutDates = policy(PolicyStatus.OPEN, ApplicationType.ALWAYS, null, null);
        Policy withDates = policy(PolicyStatus.OPEN, ApplicationType.ALWAYS,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31));

        assertThat(evaluator.evaluate(withoutDates).status()).isEqualTo(PolicyAvailability.AVAILABLE);
        assertThat(evaluator.evaluate(withoutDates).reason()).isEqualTo(PolicyAvailabilityReason.ALWAYS_APPLICATION_TYPE);
        assertThat(evaluator.evaluate(withDates).status()).isEqualTo(PolicyAvailability.AVAILABLE);
        assertThat(evaluator.evaluate(withDates).reason()).isEqualTo(PolicyAvailabilityReason.ALWAYS_APPLICATION_TYPE);
    }

    @Test
    void OPEN이고_UNKNOWN이면_NEEDS_REVIEW다() {
        Policy policy = policy(PolicyStatus.OPEN, ApplicationType.UNKNOWN, null, null);

        PolicyAvailabilityResult result = evaluator.evaluate(policy);

        assertThat(result.status()).isEqualTo(PolicyAvailability.NEEDS_REVIEW);
        assertThat(result.reason()).isEqualTo(PolicyAvailabilityReason.UNKNOWN_APPLICATION_TYPE);
    }

    @Test
    void OPEN이고_PERIOD인데_시작일과_종료일이_모두_없으면_NEEDS_REVIEW다() {
        Policy policy = policy(PolicyStatus.OPEN, ApplicationType.PERIOD, null, null);

        PolicyAvailabilityResult result = evaluator.evaluate(policy);

        assertThat(result.status()).isEqualTo(PolicyAvailability.NEEDS_REVIEW);
        assertThat(result.reason()).isEqualTo(PolicyAvailabilityReason.MISSING_APPLICATION_PERIOD);
    }

    @Test
    void OPEN이고_PERIOD인데_시작일만_없으면_NEEDS_REVIEW다() {
        Policy policy = policy(PolicyStatus.OPEN, ApplicationType.PERIOD, null, LocalDate.of(2026, 12, 31));

        PolicyAvailabilityResult result = evaluator.evaluate(policy);

        assertThat(result.status()).isEqualTo(PolicyAvailability.NEEDS_REVIEW);
        assertThat(result.reason()).isEqualTo(PolicyAvailabilityReason.MISSING_APPLICATION_PERIOD);
    }

    @Test
    void OPEN이고_PERIOD인데_종료일만_없으면_NEEDS_REVIEW다() {
        Policy policy = policy(PolicyStatus.OPEN, ApplicationType.PERIOD, LocalDate.of(2026, 1, 1), null);

        PolicyAvailabilityResult result = evaluator.evaluate(policy);

        assertThat(result.status()).isEqualTo(PolicyAvailability.NEEDS_REVIEW);
        assertThat(result.reason()).isEqualTo(PolicyAvailabilityReason.MISSING_APPLICATION_PERIOD);
    }

    @Test
    void OPEN이고_PERIOD인데_시작일이_종료일보다_늦으면_NEEDS_REVIEW다() {
        Policy policy = policy(PolicyStatus.OPEN, ApplicationType.PERIOD,
                LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1));

        PolicyAvailabilityResult result = evaluator.evaluate(policy);

        assertThat(result.status()).isEqualTo(PolicyAvailability.NEEDS_REVIEW);
        assertThat(result.reason()).isEqualTo(PolicyAvailabilityReason.INVALID_APPLICATION_PERIOD);
    }

    @Test
    void OPEN이고_PERIOD이고_오늘이_시작일_이전이면_UNAVAILABLE이다() {
        Policy policy = policy(PolicyStatus.OPEN, ApplicationType.PERIOD,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31));

        PolicyAvailabilityResult result = evaluator.evaluate(policy);

        assertThat(result.status()).isEqualTo(PolicyAvailability.UNAVAILABLE);
        assertThat(result.reason()).isEqualTo(PolicyAvailabilityReason.BEFORE_APPLICATION_PERIOD);
    }

    @Test
    void OPEN이고_PERIOD이고_오늘이_시작일과_정확히_같으면_AVAILABLE이다() {
        Policy policy = policy(PolicyStatus.OPEN, ApplicationType.PERIOD,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 31));

        PolicyAvailabilityResult result = evaluator.evaluate(policy);

        assertThat(result.status()).isEqualTo(PolicyAvailability.AVAILABLE);
        assertThat(result.reason()).isEqualTo(PolicyAvailabilityReason.WITHIN_APPLICATION_PERIOD);
    }

    @Test
    void OPEN이고_PERIOD이고_오늘이_종료일과_정확히_같으면_AVAILABLE이다() {
        Policy policy = policy(PolicyStatus.OPEN, ApplicationType.PERIOD,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 15));

        PolicyAvailabilityResult result = evaluator.evaluate(policy);

        assertThat(result.status()).isEqualTo(PolicyAvailability.AVAILABLE);
        assertThat(result.reason()).isEqualTo(PolicyAvailabilityReason.WITHIN_APPLICATION_PERIOD);
    }

    @Test
    void OPEN이고_PERIOD이고_오늘이_기간_중이면_AVAILABLE이다() {
        Policy policy = policy(PolicyStatus.OPEN, ApplicationType.PERIOD,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        PolicyAvailabilityResult result = evaluator.evaluate(policy);

        assertThat(result.status()).isEqualTo(PolicyAvailability.AVAILABLE);
        assertThat(result.reason()).isEqualTo(PolicyAvailabilityReason.WITHIN_APPLICATION_PERIOD);
    }

    @Test
    void OPEN이고_PERIOD이고_오늘이_종료일보다_늦으면_UNAVAILABLE이고_사유로_상태_불일치를_구분할_수_있다() {
        Policy policy = policy(PolicyStatus.OPEN, ApplicationType.PERIOD,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        PolicyAvailabilityResult result = evaluator.evaluate(policy);

        assertThat(result.status()).isEqualTo(PolicyAvailability.UNAVAILABLE);
        assertThat(result.reason()).isEqualTo(PolicyAvailabilityReason.AFTER_APPLICATION_PERIOD);
        assertThat(result.reason()).isNotEqualTo(PolicyAvailabilityReason.CLOSED);
    }

    private Policy policy(PolicyStatus status, ApplicationType applicationType,
                           LocalDate startDate, LocalDate endDate) {
        return Policy.builder()
                .title("테스트 정책")
                .applicationType(applicationType)
                .applicationStartDate(startDate)
                .applicationEndDate(endDate)
                .regionScope(RegionScope.NATIONAL)
                .status(status)
                .build();
    }
}
