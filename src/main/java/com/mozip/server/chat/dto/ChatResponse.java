package com.mozip.server.chat.dto;

import java.util.List;

/**
 * @param reply        답변 요약 문장(블록을 못 그리는 화면·대화 기록용)
 * @param responseType 질문 의도에 따른 답변 유형(RECOMMEND, COMPARE, HOW_TO_APPLY, ELIGIBILITY, CLARIFY, TERM,
 *                     NO_RESULT, GENERAL)
 * @param blocks       화면에 순서대로 그릴 답변 블록
 * @param followUps    답변 아래 칩으로 보여줄 후속 질문
 */
public record ChatResponse(
        String reply,
        List<ChatMatchedPolicyResponse> matchedPolicies,
        List<ChatUnresolvedConditionResponse> unresolvedConditions,
        String responseType,
        List<ChatBlockResponse> blocks,
        List<String> followUps
) {

    public ChatResponse {
        matchedPolicies = matchedPolicies != null ? List.copyOf(matchedPolicies) : List.of();
        unresolvedConditions = unresolvedConditions != null ? List.copyOf(unresolvedConditions) : List.of();
        blocks = blocks != null ? List.copyOf(blocks) : List.of();
        followUps = followUps != null ? List.copyOf(followUps) : List.of();
    }

    /** 블록 없이 만드는 생성자(하위 호환·테스트용). */
    public ChatResponse(String reply, List<ChatMatchedPolicyResponse> matchedPolicies,
                        List<ChatUnresolvedConditionResponse> unresolvedConditions) {
        this(reply, matchedPolicies, unresolvedConditions, null, List.of(), List.of());
    }
}
