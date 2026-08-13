package com.mozip.server.ai.dto;

import java.util.List;

public record SemanticMatchRequest(
        SemanticMatchUserRequest user,
        List<SemanticMatchPolicyRequest> policies
) {
}
