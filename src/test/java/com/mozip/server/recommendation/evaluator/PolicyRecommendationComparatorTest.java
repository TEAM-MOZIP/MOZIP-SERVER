package com.mozip.server.recommendation.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import com.mozip.server.recommendation.domain.PolicyRecommendationCandidate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PolicyRecommendationComparatorTest {

    private final Comparator<PolicyRecommendationCandidate> comparator = PolicyRecommendationComparator.comparator();

    @Test
    void ELIGIBLE이_NEEDS_REVIEW보다_우선한다() {
        PolicyRecommendationCandidate eligible = candidate(1L, EligibilityStatus.ELIGIBLE, null, null);
        PolicyRecommendationCandidate needsReview = candidate(2L, EligibilityStatus.NEEDS_REVIEW, null, null);

        assertThat(comparator.compare(eligible, needsReview)).isNegative();
    }

    @Test
    void NEEDS_REVIEW가_INELIGIBLE보다_우선한다() {
        PolicyRecommendationCandidate needsReview = candidate(1L, EligibilityStatus.NEEDS_REVIEW, null, null);
        PolicyRecommendationCandidate ineligible = candidate(2L, EligibilityStatus.INELIGIBLE, null, null);

        assertThat(comparator.compare(needsReview, ineligible)).isNegative();
    }

    @Test
    void enum_선언_순서가_아니라_명시적_우선순위를_따른다() {
        // EligibilityStatus 선언 순서는 ELIGIBLE, INELIGIBLE, NEEDS_REVIEW라
        // ordinal 기준이면 INELIGIBLE이 NEEDS_REVIEW보다 앞서야 하지만, 실제로는 반대로 정렬되어야 한다.
        PolicyRecommendationCandidate needsReview = candidate(1L, EligibilityStatus.NEEDS_REVIEW, null, null);
        PolicyRecommendationCandidate ineligible = candidate(2L, EligibilityStatus.INELIGIBLE, null, null);

        List<PolicyRecommendationCandidate> sorted = List.of(ineligible, needsReview).stream()
                .sorted(comparator)
                .toList();

        assertThat(sorted).containsExactly(needsReview, ineligible);
    }

    @Test
    void 같은_상태에서는_신청_마감일이_이른_정책이_우선한다() {
        PolicyRecommendationCandidate earlier =
                candidate(1L, EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 8, 1), null);
        PolicyRecommendationCandidate later =
                candidate(2L, EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 9, 1), null);

        assertThat(comparator.compare(earlier, later)).isNegative();
    }

    @Test
    void 마감일이_없는_상시모집_정책은_마감일이_있는_정책보다_뒤로_간다() {
        PolicyRecommendationCandidate hasDeadline =
                candidate(1L, EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 8, 1), null);
        PolicyRecommendationCandidate always = candidate(2L, EligibilityStatus.ELIGIBLE, null, null);

        assertThat(comparator.compare(hasDeadline, always)).isNegative();
    }

    @Test
    void 상태와_마감일이_같으면_등록일이_최신인_정책이_우선한다() {
        LocalDateTime earlierCreatedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime laterCreatedAt = LocalDateTime.of(2026, 1, 2, 0, 0);
        PolicyRecommendationCandidate older =
                candidate(1L, EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 8, 1), earlierCreatedAt);
        PolicyRecommendationCandidate newer =
                candidate(2L, EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 8, 1), laterCreatedAt);

        assertThat(comparator.compare(newer, older)).isNegative();
    }

    @Test
    void 모든_기준이_같으면_id가_작은_정책이_우선한다() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        PolicyRecommendationCandidate smallerId =
                candidate(1L, EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 8, 1), createdAt);
        PolicyRecommendationCandidate largerId =
                candidate(2L, EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 8, 1), createdAt);

        assertThat(comparator.compare(smallerId, largerId)).isNegative();
    }

    private PolicyRecommendationCandidate candidate(Long id, EligibilityStatus status, LocalDate applicationEndDate,
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

        PolicyEligibilityResult eligibilityResult = new PolicyEligibilityResult(status, "테스트 사유", List.of());
        PolicyAvailabilityResult availabilityResult =
                new PolicyAvailabilityResult(PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.WITHIN_APPLICATION_PERIOD);

        return new PolicyRecommendationCandidate(policy, eligibilityResult, availabilityResult);
    }
}
