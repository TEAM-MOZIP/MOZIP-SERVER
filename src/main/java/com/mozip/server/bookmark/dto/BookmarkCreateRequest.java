package com.mozip.server.bookmark.dto;

import jakarta.validation.constraints.NotNull;

public record BookmarkCreateRequest(
        @NotNull Long policyId
) {
}
