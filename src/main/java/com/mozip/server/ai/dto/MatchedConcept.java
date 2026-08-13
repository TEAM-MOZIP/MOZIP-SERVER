package com.mozip.server.ai.dto;

public record MatchedConcept(
        MappingAxis axis,
        String userConceptUri,
        String userConceptCode,
        String policyConceptUri,
        String policyConceptCode
) {
}
