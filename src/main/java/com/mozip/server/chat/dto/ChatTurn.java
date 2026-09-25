package com.mozip.server.chat.dto;

import java.util.List;

/**
 * FRONT ↔ SERVER {@code /api/chat/messages} 계약의 history 항목이다. FRONT가
 * sessionStorage 등으로 관리하는 이전 turn(질문/답변)을 그대로 표현한다. SERVER는
 * 이 값을 분석·수정하지 않고 {@code ai.dto.ChatTurn}으로 변환해 AI에 전달만 한다.
 *
 * @param policyIds 그 턴의 답변에 카드로 보여준 정책 id(선택). "신청 방법 알려줘", "두 정책 비교해줘" 같은
 *                  후속 질문이 어떤 정책을 가리키는지 알기 위해 쓴다.
 */
public record ChatTurn(
        String message,
        String reply,
        List<Long> policyIds
) {

    public ChatTurn {
        policyIds = policyIds != null ? List.copyOf(policyIds) : List.of();
    }

    public ChatTurn(String message, String reply) {
        this(message, reply, List.of());
    }
}
