package com.mozip.server.policy.dto;

import jakarta.validation.constraints.NotBlank;

public record TermExplanationRequest(
        @NotBlank String term,
        @NotBlank String context
) {
}
