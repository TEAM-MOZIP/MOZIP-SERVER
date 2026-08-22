package com.mozip.server.chat.dto;

import com.mozip.server.ai.dto.ConditionAxis;
import com.mozip.server.ai.dto.UnresolvedCondition;

public record ChatUnresolvedConditionResponse(
        ConditionAxis axis,
        String rawText
) {

    public static ChatUnresolvedConditionResponse from(UnresolvedCondition condition) {
        return new ChatUnresolvedConditionResponse(condition.axis(), condition.rawText());
    }
}
