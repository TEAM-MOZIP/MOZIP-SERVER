package com.mozip.server.ai.dto;

/**
 * SERVER ↔ AI {@code chat/respond} 계약의 history 항목이다. {@code chat.dto.ChatTurn}
 * (FRONT ↔ SERVER 계약)과 필드 구조는 같지만 별도 타입으로 유지한다 — AI wire DTO가
 * SERVER 내부/공개 DTO를 알아야 하는 상황을 만들지 않기 위함이다({@link GroundingPolicy}/
 * {@link PolicyDetailGrounding}과 동일한 원칙).
 */
public record ChatTurn(
        String message,
        String reply
) {
}
