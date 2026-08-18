package com.mozip.server.ai.dto;

import java.util.List;

public record ApplicationGuideResponse(
        List<ApplicationGuideStep> steps,
        List<String> requiredDocuments
) {
}
