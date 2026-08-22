package com.mozip.server.ai.dto;

public record PolicyDetailGrounding(
        String title,
        String summary,
        String eligibility,
        String applicationPeriod,
        String organization
) {
}
