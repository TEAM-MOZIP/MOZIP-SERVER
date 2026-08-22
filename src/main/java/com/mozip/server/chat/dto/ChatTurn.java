package com.mozip.server.chat.dto;

/**
 * FRONT ↔ SERVER {@code /api/chat/messages} 계약의 history 항목이다. FRONT가
 * sessionStorage 등으로 관리하는 이전 turn(질문/답변)을 그대로 표현한다. SERVER는
 * 이 값을 분석·수정하지 않고 {@code ai.dto.ChatTurn}으로 변환해 AI에 전달만 한다.
 */
public record ChatTurn(
        String message,
        String reply
) {
}
