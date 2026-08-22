package com.mozip.server.chat.dto;

import java.util.List;

public record ChatResponse(
        String reply,
        List<ChatMatchedPolicyResponse> matchedPolicies,
        List<ChatUnresolvedConditionResponse> unresolvedConditions
) {
}
