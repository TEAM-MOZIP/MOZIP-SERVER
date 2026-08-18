package com.mozip.server.ai.dto;

public record ApplicationGuideRequest(
        String applicationInstructions,
        String requiredDocumentsSource
) {
}
