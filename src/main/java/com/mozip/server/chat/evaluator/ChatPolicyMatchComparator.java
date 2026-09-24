package com.mozip.server.chat.evaluator;

import com.mozip.server.chat.dto.ChatPolicyMatchResult;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import java.util.Comparator;

/**
 * top 5 grounding 선정을 위한 정렬 전용 Comparator다. 기존
 * {@code PolicyRecommendationComparator}는 {@code PolicyRecommendationCandidate}
 * (availabilityResult/semanticScore 필수)에 타입이 고정돼 있어 D-1의
 * {@link ChatPolicyMatchResult}로는 재사용할 수 없다 — semanticScore 비교 단계만
 * 제거한 형태로 신규 정의한다. INELIGIBLE 제외는 이 Comparator의 책임이 아니라
 * 호출자(ChatService)가 정렬 이전에 수행한다.
 *
 * <p>정렬 우선순위: 키워드 관련도 점수(높은 순) → 대상 특정도(나이·지역을 콕 집는 정책 먼저)
 * → eligibility(ELIGIBLE → NEEDS_REVIEW) → 마감일(빠른 순)
 * → 등록일(최신 순) → id. 정책 수가 수천 건 규모가 되면서 "질문 주제와 관련 있는 정책"이 먼저 오도록
 * 관련도를 최우선으로 둔다. 키워드가 없으면 모든 점수가 0이라 기존 순서와 동일하다.
 */
public class ChatPolicyMatchComparator {

    private static final Comparator<ChatPolicyMatchResult> INSTANCE = Comparator
            .comparing(ChatPolicyMatchResult::relevanceScore, Comparator.reverseOrder())
            .thenComparing(ChatPolicyMatchResult::targetingScore, Comparator.reverseOrder())
            .thenComparingInt(match -> statusPriority(match.eligibilityResult().overallStatus()))
            .thenComparing(match -> match.policy().getApplicationEndDate(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(match -> match.policy().getCreatedAt(), Comparator.reverseOrder())
            .thenComparing(match -> match.policy().getId());

    private ChatPolicyMatchComparator() {
    }

    public static Comparator<ChatPolicyMatchResult> comparator() {
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
