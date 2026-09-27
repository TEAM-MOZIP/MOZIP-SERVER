package com.mozip.server.chat.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatPolicyRelevanceScorerTest {

    @Test
    void 제목_요약_본문_순으로_가중치를_두고_키워드별로_합산한다() {
        Policy policy = policy("서울시 청년월세지원", "청년 주거비 부담 완화", "월 20만원 월세 지원");

        // 청년: 제목 3 + 요약 2 = 5, 월세: 제목 3 + 본문 1 = 4
        assertThat(ChatPolicyRelevanceScorer.score(policy, List.of("청년", "월세"))).isEqualTo(9);
    }

    @Test
    void 공백을_무시하고_비교한다() {
        Policy policy = policy("청년 월세 지원", null, null);

        assertThat(ChatPolicyRelevanceScorer.score(policy, List.of("청년월세"))).isEqualTo(3);
    }

    @Test
    void 키워드가_없거나_매칭되지_않으면_0점이다() {
        Policy policy = policy("국민취업지원제도", "취업 지원", null);

        assertThat(ChatPolicyRelevanceScorer.score(policy, List.of())).isZero();
        assertThat(ChatPolicyRelevanceScorer.score(policy, List.of("월세"))).isZero();
    }

    @Test
    void 제목_매칭_여부는_제목만_본다() {
        Policy policy = policy("저소득주민 건강보험료 지원", "기준중위소득 50% 이하 가구 지원", null);

        assertThat(ChatPolicyRelevanceScorer.matchesTitle(policy, List.of("기준중위소득"))).isFalse();
        assertThat(ChatPolicyRelevanceScorer.matchesTitle(policy, List.of("건강보험료"))).isTrue();
    }

    @Test
    void 제목_요약_매칭은_본문_지원대상만_걸린_정책을_제외한다() {
        Policy summaryHit = policy("생활안정자금", "대학생 생활비 지원", null);
        Policy bodyOnly = Policy.builder()
                .title("생활안정자금 융자")
                .targetDescription("청년, 대학생, 구직자 등")
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.ALWAYS_OPEN)
                .build();

        assertThat(ChatPolicyRelevanceScorer.matchesTitleOrSummary(summaryHit, List.of("대학생"))).isTrue();
        assertThat(ChatPolicyRelevanceScorer.matchesTitleOrSummary(bodyOnly, List.of("대학생"))).isFalse();
        assertThat(ChatPolicyRelevanceScorer.score(bodyOnly, List.of("대학생"))).isEqualTo(1);
    }

    private Policy policy(String title, String summary, String benefitDescription) {
        return Policy.builder()
                .title(title)
                .summary(summary)
                .benefitDescription(benefitDescription)
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.ALWAYS_OPEN)
                .build();
    }
}
