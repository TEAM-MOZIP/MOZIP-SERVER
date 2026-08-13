package com.mozip.server.ai.dto;

import java.util.List;

public record SemanticMatchResult(
        Long policyId,
        Double semanticScore,
        List<MatchedConcept> matchedConcepts,
        List<InferencePath> inferencePaths
) {
}
