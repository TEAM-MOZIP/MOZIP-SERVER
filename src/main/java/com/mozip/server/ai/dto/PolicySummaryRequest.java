package com.mozip.server.ai.dto;

public record PolicySummaryRequest(
        String title,
        String description,
        String targetDescription,
        String benefitDescription
) {
}
