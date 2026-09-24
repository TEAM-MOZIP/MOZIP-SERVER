package com.mozip.server.chat.dto;

import com.mozip.server.policy.entity.Policy;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;

/**
 * @param relevanceScore 메시지 키워드와 정책의 관련도 점수({@code ChatPolicyRelevanceScorer}). 키워드가 없으면 0이다.
 * @param targetingScore 정책이 사용자 조건을 "콕 집어" 대상으로 하는 정도. 나이 제한이 명시돼 있고 사용자 나이가
 *                       그 안이면 +1, 사용자 지역(자치구 또는 상위 서울)에 한정된 지역 정책이면 +1.
 *                       누구나 받는 정책보다 "20대 청년 정책", "양천구 정책"을 앞에 두기 위한 정렬 기준이다.
 */
public record ChatPolicyMatchResult(
        Policy policy,
        PolicyEligibilityResult eligibilityResult,
        int relevanceScore,
        int targetingScore
) {

    public ChatPolicyMatchResult(Policy policy, PolicyEligibilityResult eligibilityResult) {
        this(policy, eligibilityResult, 0, 0);
    }

    public ChatPolicyMatchResult(Policy policy, PolicyEligibilityResult eligibilityResult, int targetingScore) {
        this(policy, eligibilityResult, 0, targetingScore);
    }

    public ChatPolicyMatchResult withRelevanceScore(int score) {
        return new ChatPolicyMatchResult(policy, eligibilityResult, score, targetingScore);
    }
}
