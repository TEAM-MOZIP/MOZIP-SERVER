package com.mozip.server.policy.dto;

import com.mozip.server.ai.dto.ApplicationGuideStep;

public record ApplicationGuideStepResponse(
        Integer order,
        String title,
        String description
) {

    public static ApplicationGuideStepResponse from(ApplicationGuideStep step) {
        return new ApplicationGuideStepResponse(step.order(), step.title(), step.description());
    }
}
