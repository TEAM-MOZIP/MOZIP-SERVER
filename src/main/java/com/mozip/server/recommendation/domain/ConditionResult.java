package com.mozip.server.recommendation.domain;

public record ConditionResult(
        ConditionType type,
        ConditionStatus status,
        String reason
) {
}
