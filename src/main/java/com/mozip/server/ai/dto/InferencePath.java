package com.mozip.server.ai.dto;

import java.util.List;

public record InferencePath(
        MappingAxis axis,
        String fromConceptUri,
        List<String> relations,
        String toConceptUri
) {
}
