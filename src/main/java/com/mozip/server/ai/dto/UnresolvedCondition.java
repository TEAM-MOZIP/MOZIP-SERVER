package com.mozip.server.ai.dto;

/**
 * conditions/extract 응답과 chat/respond 요청 양쪽에서 동일한 wire shape을 쓰는
 * {axis, rawText}다. AI 쪽은 두 schema 파일에 같은 모양이 각각 정의돼 있지만, SERVER는
 * 변환 없이 그대로 전달하므로 하나의 타입으로 공유한다.
 */
public record UnresolvedCondition(
        ConditionAxis axis,
        String rawText
) {
}
