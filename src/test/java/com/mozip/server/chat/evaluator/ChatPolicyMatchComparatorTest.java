package com.mozip.server.chat.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.chat.dto.ChatPolicyMatchResult;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ChatPolicyMatchComparatorTest {

    @Test
    void ELIGIBLE이_NEEDS_REVIEW보다_먼저_정렬된다() {
        ChatPolicyMatchResult needsReview = match(1L, EligibilityStatus.NEEDS_REVIEW, null, null);
        ChatPolicyMatchResult eligible = match(2L, EligibilityStatus.ELIGIBLE, null, null);

        List<ChatPolicyMatchResult> sorted = sort(needsReview, eligible);

        assertThat(sorted).containsExactly(eligible, needsReview);
    }

    @Test
    void 동일_상태면_applicationEndDate가_빠른_순으로_정렬된다() {
        ChatPolicyMatchResult later = match(1L, EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 12, 31), null);
        ChatPolicyMatchResult earlier = match(2L, EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 6, 30), null);

        List<ChatPolicyMatchResult> sorted = sort(later, earlier);

        assertThat(sorted).containsExactly(earlier, later);
    }

    @Test
    void applicationEndDate가_null이면_최하위로_정렬된다() {
        ChatPolicyMatchResult withDate = match(1L, EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 6, 30), null);
        ChatPolicyMatchResult withoutDate = match(2L, EligibilityStatus.ELIGIBLE, null, null);

        List<ChatPolicyMatchResult> sorted = sort(withoutDate, withDate);

        assertThat(sorted).containsExactly(withDate, withoutDate);
    }

    @Test
    void applicationEndDate까지_같으면_createdAt이_최신_순으로_정렬된다() {
        ChatPolicyMatchResult older = match(1L, EligibilityStatus.ELIGIBLE, null, LocalDateTime.of(2026, 1, 1, 0, 0));
        ChatPolicyMatchResult newer = match(2L, EligibilityStatus.ELIGIBLE, null, LocalDateTime.of(2026, 6, 1, 0, 0));

        List<ChatPolicyMatchResult> sorted = sort(older, newer);

        assertThat(sorted).containsExactly(newer, older);
    }

    @Test
    void createdAt까지_같으면_policyId_오름차순으로_정렬된다() {
        LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        ChatPolicyMatchResult larger = match(5L, EligibilityStatus.ELIGIBLE, null, sameCreatedAt);
        ChatPolicyMatchResult smaller = match(3L, EligibilityStatus.ELIGIBLE, null, sameCreatedAt);

        List<ChatPolicyMatchResult> sorted = sort(larger, smaller);

        assertThat(sorted).containsExactly(smaller, larger);
    }

    private List<ChatPolicyMatchResult> sort(ChatPolicyMatchResult... matches) {
        return List.of(matches).stream().sorted(ChatPolicyMatchComparator.comparator()).toList();
    }

    private ChatPolicyMatchResult match(Long id, EligibilityStatus status, LocalDate applicationEndDate, LocalDateTime createdAt) {
        Policy policy = Policy.builder()
                .title("테스트 정책 " + id)
                .applicationType(ApplicationType.PERIOD)
                .applicationEndDate(applicationEndDate)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        if (createdAt != null) {
            ReflectionTestUtils.setField(policy, "createdAt", createdAt);
        }
        PolicyEligibilityResult eligibilityResult = new PolicyEligibilityResult(status, "테스트", List.of());
        return new ChatPolicyMatchResult(policy, eligibilityResult);
    }
}
