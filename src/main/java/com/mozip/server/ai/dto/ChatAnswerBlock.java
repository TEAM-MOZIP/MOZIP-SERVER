package com.mozip.server.ai.dto;

import java.util.List;

/**
 * AI가 생성한 답변 블록 하나. {@code type}에 따라 쓰는 필드만 채워진다.
 * <ul>
 *   <li>TEXT·CONCLUSION: text</li>
 *   <li>POLICY_GROUP: title + policies(정책 id·추천 이유·핵심 정보)</li>
 *   <li>COMPARISON: policyIds(열) + rows</li>
 *   <li>STEPS: title + steps / CHECKLIST: title + items</li>
 *   <li>TERM: title(용어) + text(정의) + example</li>
 *   <li>QUICK_REPLIES: text(질문, 선택) + items</li>
 * </ul>
 */
public record ChatAnswerBlock(
        String type,
        String text,
        String title,
        String example,
        List<Policy> policies,
        List<Long> policyIds,
        List<Row> rows,
        List<Step> steps,
        List<String> items
) {

    public ChatAnswerBlock {
        policies = policies != null ? List.copyOf(policies) : List.of();
        policyIds = policyIds != null ? List.copyOf(policyIds) : List.of();
        rows = rows != null ? List.copyOf(rows) : List.of();
        steps = steps != null ? List.copyOf(steps) : List.of();
        items = items != null ? List.copyOf(items) : List.of();
    }

    public static ChatAnswerBlock text(String type, String text) {
        return new ChatAnswerBlock(type, text, null, null, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public record Policy(Long policyId, String reason, String highlight) {
    }

    public record Row(String label, List<String> values) {

        public Row {
            values = values != null ? List.copyOf(values) : List.of();
        }
    }

    public record Step(String title, String description) {
    }
}
