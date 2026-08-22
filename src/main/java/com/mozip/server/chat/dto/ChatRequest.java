package com.mozip.server.chat.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ChatRequest(
        @NotBlank String message,
        List<ChatTurn> history
) {

    public ChatRequest {
        history = history != null ? history : List.of();
    }
}
