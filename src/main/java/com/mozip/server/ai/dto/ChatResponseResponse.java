package com.mozip.server.ai.dto;

import java.util.List;

/**
 * AI {@code chat/respond} 응답. 답변은 {@code blocks}로 화면에 그리고, {@code reply}는 한두 문장 요약이다
 * (블록을 못 그리는 화면·대화 기록용).
 *
 * @param responseType 질문 의도에 따른 답변 유형(RECOMMEND, COMPARE, HOW_TO_APPLY, ELIGIBILITY, CLARIFY, TERM,
 *                     NO_RESULT, GENERAL)
 * @param followUps    답변 아래 칩으로 보여줄 후속 질문
 */
public record ChatResponseResponse(
        String reply,
        String responseType,
        List<ChatAnswerBlock> blocks,
        List<String> followUps
) {

    public ChatResponseResponse {
        blocks = blocks != null ? List.copyOf(blocks) : List.of();
        followUps = followUps != null ? List.copyOf(followUps) : List.of();
    }

    /** 블록 없이 요약 문장만 있는 응답(하위 호환·테스트용). */
    public ChatResponseResponse(String reply) {
        this(reply, null, List.of(), List.of());
    }
}
