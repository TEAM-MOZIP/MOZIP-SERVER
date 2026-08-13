package com.mozip.server.ai.dto;

import java.util.List;

public record SemanticMatchResponse(
        List<SemanticMatchResult> results
) {
}
