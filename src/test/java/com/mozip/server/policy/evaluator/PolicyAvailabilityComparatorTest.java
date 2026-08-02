package com.mozip.server.policy.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityCandidate;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PolicyAvailabilityComparatorTest {

    private final Comparator<PolicyAvailabilityCandidate> comparator = PolicyAvailabilityComparator.comparator();

    @Test
    void AVAILABLE이_NEEDS_REVIEW보다_우선한다() {
        PolicyAvailabilityCandidate available = candidate(1L, PolicyAvailability.AVAILABLE, null, null);
        PolicyAvailabilityCandidate needsReview = candidate(2L, PolicyAvailability.NEEDS_REVIEW, null, null);

        assertThat(comparator.compare(available, needsReview)).isNegative();
    }

    @Test
    void NEEDS_REVIEW가_UNAVAILABLE보다_우선한다() {
        PolicyAvailabilityCandidate needsReview = candidate(1L, PolicyAvailability.NEEDS_REVIEW, null, null);
        PolicyAvailabilityCandidate unavailable = candidate(2L, PolicyAvailability.UNAVAILABLE, null, null);

        assertThat(comparator.compare(needsReview, unavailable)).isNegative();
    }

    @Test
    void enum_선언_순서가_아니라_명시적_우선순위를_따른다() {
        // PolicyAvailability 선언 순서는 AVAILABLE, UNAVAILABLE, NEEDS_REVIEW라
        // ordinal 기준이면 UNAVAILABLE이 NEEDS_REVIEW보다 앞서야 하지만, 실제로는 반대로 정렬되어야 한다.
        PolicyAvailabilityCandidate needsReview = candidate(1L, PolicyAvailability.NEEDS_REVIEW, null, null);
        PolicyAvailabilityCandidate unavailable = candidate(2L, PolicyAvailability.UNAVAILABLE, null, null);

        List<PolicyAvailabilityCandidate> sorted = List.of(unavailable, needsReview).stream()
                .sorted(comparator)
                .toList();

        assertThat(sorted).containsExactly(needsReview, unavailable);
    }

    @Test
    void 같은_상태에서는_신청_마감일이_이른_정책이_우선한다() {
        PolicyAvailabilityCandidate earlier =
                candidate(1L, PolicyAvailability.AVAILABLE, LocalDate.of(2026, 8, 1), null);
        PolicyAvailabilityCandidate later =
                candidate(2L, PolicyAvailability.AVAILABLE, LocalDate.of(2026, 9, 1), null);

        assertThat(comparator.compare(earlier, later)).isNegative();
    }

    @Test
    void 마감일이_없는_상시모집_정책은_마감일이_있는_정책보다_뒤로_간다() {
        PolicyAvailabilityCandidate hasDeadline =
                candidate(1L, PolicyAvailability.AVAILABLE, LocalDate.of(2026, 8, 1), null);
        PolicyAvailabilityCandidate always = candidate(2L, PolicyAvailability.AVAILABLE, null, null);

        assertThat(comparator.compare(hasDeadline, always)).isNegative();
    }

    @Test
    void 상태와_마감일이_같으면_등록일이_최신인_정책이_우선한다() {
        LocalDateTime earlierCreatedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime laterCreatedAt = LocalDateTime.of(2026, 1, 2, 0, 0);
        PolicyAvailabilityCandidate older =
                candidate(1L, PolicyAvailability.AVAILABLE, LocalDate.of(2026, 8, 1), earlierCreatedAt);
        PolicyAvailabilityCandidate newer =
                candidate(2L, PolicyAvailability.AVAILABLE, LocalDate.of(2026, 8, 1), laterCreatedAt);

        assertThat(comparator.compare(newer, older)).isNegative();
    }

    @Test
    void 모든_기준이_같으면_id가_작은_정책이_우선한다() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        PolicyAvailabilityCandidate smallerId =
                candidate(1L, PolicyAvailability.AVAILABLE, LocalDate.of(2026, 8, 1), createdAt);
        PolicyAvailabilityCandidate largerId =
                candidate(2L, PolicyAvailability.AVAILABLE, LocalDate.of(2026, 8, 1), createdAt);

        assertThat(comparator.compare(smallerId, largerId)).isNegative();
    }

    private PolicyAvailabilityCandidate candidate(Long id, PolicyAvailability status, LocalDate applicationEndDate,
                                                    LocalDateTime createdAt) {
        Policy policy = Policy.builder()
                .title("테스트 정책 " + id)
                .applicationType(ApplicationType.PERIOD)
                .applicationEndDate(applicationEndDate)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        ReflectionTestUtils.setField(policy, "createdAt", createdAt);

        PolicyAvailabilityResult availabilityResult =
                new PolicyAvailabilityResult(status, PolicyAvailabilityReason.WITHIN_APPLICATION_PERIOD, false);

        return new PolicyAvailabilityCandidate(policy, availabilityResult);
    }
}
