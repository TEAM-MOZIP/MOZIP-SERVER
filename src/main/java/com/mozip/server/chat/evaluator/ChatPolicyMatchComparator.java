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
 */
public class ChatPolicyMatchComparator {

    private static final Comparator<ChatPolicyMatchResult> INSTANCE = Comparator
            .comparingInt((ChatPolicyMatchResult match) -> statusPriority(match.eligibilityResult().overallStatus()))
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
