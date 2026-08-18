package com.mozip.server.ai.dto;

import com.mozip.server.policy.dto.TermExplanationRequest;

public record TermExplainRequest(
        String term,
        String context
) {

    public static TermExplainRequest from(TermExplanationRequest request) {
        return new TermExplainRequest(request.term(), request.context());
    }
}
