package com.mozip.server.chat.dto;

import com.mozip.server.policy.entity.Policy;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;

/**
 * @param relevanceScore 메시지 키워드와 정책의 관련도 점수({@code ChatPolicyRelevanceScorer}). 키워드가 없으면 0이다.
 */
public record ChatPolicyMatchResult(
        Policy policy,
        PolicyEligibilityResult eligibilityResult,
        int relevanceScore
) {

    public ChatPolicyMatchResult(Policy policy, PolicyEligibilityResult eligibilityResult) {
        this(policy, eligibilityResult, 0);
    }

    public ChatPolicyMatchResult withRelevanceScore(int score) {
        return new ChatPolicyMatchResult(policy, eligibilityResult, score);
    }
}
