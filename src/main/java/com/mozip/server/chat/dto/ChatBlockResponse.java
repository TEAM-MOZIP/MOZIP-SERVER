package com.mozip.server.chat.dto;

import com.mozip.server.recommendation.domain.ConditionResult;
import java.util.List;

/**
 * 챗봇 답변 블록. {@code type}에 따라 쓰는 필드만 채워진다.
 * <ul>
 *   <li>TEXT·CONCLUSION: text</li>
 *   <li>POLICY_GROUP: title + policies(카드 정보까지 채운 정책)</li>
 *   <li>COMPARISON: columns(비교한 정책) + rows(values는 columns 순서)</li>
 *   <li>STEPS: title + steps / CHECKLIST: title + items</li>
 *   <li>TERM: title(용어) + text(정의) + example</li>
 *   <li>QUICK_REPLIES: text(질문, 선택) + items(누르면 그대로 보낼 답)</li>
 *   <li>CONDITIONS: conditions(조건별 자격 판정) — SERVER가 붙인다</li>
 *   <li>LINKS: links(신청·원문 주소) — SERVER가 붙인다</li>
 * </ul>
 */
public record ChatBlockResponse(
        String type,
        String text,
        String title,
        String example,
        List<ChatPolicyCardResponse> policies,
        List<Column> columns,
        List<Row> rows,
        List<Step> steps,
        List<String> items,
        List<ConditionResult> conditions,
        Links links
) {

    public ChatBlockResponse {
        policies = policies != null ? List.copyOf(policies) : List.of();
        columns = columns != null ? List.copyOf(columns) : List.of();
        rows = rows != null ? List.copyOf(rows) : List.of();
        steps = steps != null ? List.copyOf(steps) : List.of();
        items = items != null ? List.copyOf(items) : List.of();
        conditions = conditions != null ? List.copyOf(conditions) : List.of();
    }

    public static ChatBlockResponse text(String type, String text) {
        return new ChatBlockResponse(type, text, null, null, null, null, null, null, null, null, null);
    }

    public static ChatBlockResponse conditions(List<ConditionResult> conditions) {
        return new ChatBlockResponse("CONDITIONS", null, null, null, null, null, null, null, null, conditions, null);
    }

    public static ChatBlockResponse links(String applicationUrl, String sourceUrl) {
        return new ChatBlockResponse("LINKS", null, null, null, null, null, null, null, null, null,
                new Links(applicationUrl, sourceUrl));
    }

    public record Column(Long policyId, String title) {
    }

    public record Row(String label, List<String> values) {

        public Row {
            values = values != null ? List.copyOf(values) : List.of();
        }
    }

    public record Step(String title, String description) {
    }

    public record Links(String applicationUrl, String sourceUrl) {
    }
}
