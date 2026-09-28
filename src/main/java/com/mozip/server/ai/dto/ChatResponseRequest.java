package com.mozip.server.ai.dto;

import java.util.List;

public record ChatResponseRequest(
        String message,
        List<GroundingPolicy> groundingPolicies,
        PolicyDetailGrounding policyDetail,
        List<UnresolvedCondition> unresolvedConditions,
        List<ChatTurn> history,
        /** 서버가 추출한 사용자 조건. null이면 조건 없이 탐색한 경우. */
        UserConditionGrounding userCondition
) {
}
