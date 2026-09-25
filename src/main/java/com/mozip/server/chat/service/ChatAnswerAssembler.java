package com.mozip.server.chat.service;

import com.mozip.server.ai.dto.ApplicationGuideResponse;
import com.mozip.server.ai.dto.ChatAnswerBlock;
import com.mozip.server.ai.dto.ChatResponseResponse;
import com.mozip.server.chat.dto.ChatBlockResponse;
import com.mozip.server.chat.dto.ChatMatchedPolicyResponse;
import com.mozip.server.chat.dto.ChatPolicyCardResponse;
import com.mozip.server.chat.dto.ChatResponse;
import com.mozip.server.chat.dto.ChatUnresolvedConditionResponse;
import com.mozip.server.policy.dto.PolicySummaryResponse;
import com.mozip.server.policy.service.PolicyService;
import com.mozip.server.recommendation.domain.ConditionResult;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * AI가 만든 답변 블록을 화면용 블록으로 바꾼다.
 * <ul>
 *   <li>정책 카드·비교표 열에 정책 목록 카드 정보(접수 상태·카테고리·지역·대상 나이)를 채운다. 없는 정책은 버린다.</li>
 *   <li>자격 확인 답변에는 SERVER가 판정한 조건별 결과(CONDITIONS)를, 신청 방법 답변에는 신청·원문 링크(LINKS)를 붙인다
 *       — 판정과 링크는 AI가 만들지 않는다.</li>
 * </ul>
 */
class ChatAnswerAssembler {

    static final String TEXT = "TEXT";
    static final String POLICY_GROUP = "POLICY_GROUP";
    static final String COMPARISON = "COMPARISON";
    static final String CONCLUSION = "CONCLUSION";
    static final String STEPS = "STEPS";
    static final String CHECKLIST = "CHECKLIST";
    static final String TERM = "TERM";
    static final String QUICK_REPLIES = "QUICK_REPLIES";

    static final String ELIGIBILITY = "ELIGIBILITY";
    static final String HOW_TO_APPLY = "HOW_TO_APPLY";
    static final String GENERAL = "GENERAL";

    private static final int MIN_COMPARISON_COLUMNS = 2;

    private final PolicyService policyService;

    ChatAnswerAssembler(PolicyService policyService) {
        this.policyService = policyService;
    }

    /**
     * @param eligibilityByPolicyId 사용자가 말한 조건으로 판정한 정책별 자격. 조건이 없었으면 빈 맵(카드에 자격 칩을 붙이지 않음)
     * @param detail                특정 정책 질문일 때만(조건별 판정·링크), 아니면 null
     */
    ChatResponse assemble(ChatResponseResponse answer, Map<Long, EligibilityStatus> eligibilityByPolicyId,
                          DetailContext detail, List<ChatMatchedPolicyResponse> matchedPolicies,
                          List<ChatUnresolvedConditionResponse> unresolvedConditions) {
        Map<Long, PolicySummaryResponse> summaries = policyService.getSummariesByIds(referencedPolicyIds(answer));
        String responseType = answer.responseType() != null ? answer.responseType() : GENERAL;

        List<ChatBlockResponse> blocks = new ArrayList<>();
        for (ChatAnswerBlock block : answer.blocks()) {
            ChatBlockResponse converted = convert(block, summaries, eligibilityByPolicyId);
            if (converted != null) {
                blocks.add(converted);
            }
        }
        if (blocks.isEmpty()) {
            blocks.add(ChatBlockResponse.text(TEXT, answer.reply()));
        }
        if (detail != null) {
            addServerBlocks(responseType, blocks, detail);
        }

        return new ChatResponse(answer.reply(), matchedPolicies, unresolvedConditions, responseType, blocks,
                answer.followUps());
    }

    private List<Long> referencedPolicyIds(ChatResponseResponse answer) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ChatAnswerBlock block : answer.blocks()) {
            block.policies().stream().map(ChatAnswerBlock.Policy::policyId).filter(Objects::nonNull).forEach(ids::add);
            block.policyIds().stream().filter(Objects::nonNull).forEach(ids::add);
        }
        return List.copyOf(ids);
    }

    private ChatBlockResponse convert(ChatAnswerBlock block, Map<Long, PolicySummaryResponse> summaries,
                                      Map<Long, EligibilityStatus> eligibilityByPolicyId) {
        if (block.type() == null) {
            return null;
        }
        return switch (block.type()) {
            case TEXT, CONCLUSION -> isBlank(block.text()) ? null : ChatBlockResponse.text(block.type(), block.text());
            case POLICY_GROUP -> policyGroup(block, summaries, eligibilityByPolicyId);
            case COMPARISON -> comparison(block, summaries);
            case STEPS -> block.steps().isEmpty() ? null : new ChatBlockResponse(STEPS, null, block.title(), null,
                    null, null, null,
                    block.steps().stream().map(step -> new ChatBlockResponse.Step(step.title(), step.description()))
                            .toList(),
                    null, null, null);
            case CHECKLIST -> block.items().isEmpty() ? null : new ChatBlockResponse(CHECKLIST, null, block.title(),
                    null, null, null, null, null, block.items(), null, null);
            case TERM -> isBlank(block.title()) || isBlank(block.text()) ? null : new ChatBlockResponse(TERM,
                    block.text(), block.title(), block.example(), null, null, null, null, null, null, null);
            case QUICK_REPLIES -> block.items().isEmpty() ? null : new ChatBlockResponse(QUICK_REPLIES, block.text(),
                    null, null, null, null, null, null, block.items(), null, null);
            default -> null;
        };
    }

    private ChatBlockResponse policyGroup(ChatAnswerBlock block, Map<Long, PolicySummaryResponse> summaries,
                                          Map<Long, EligibilityStatus> eligibilityByPolicyId) {
        List<ChatPolicyCardResponse> cards = block.policies().stream()
                .filter(policy -> summaries.containsKey(policy.policyId()))
                .map(policy -> ChatPolicyCardResponse.of(summaries.get(policy.policyId()), policy.reason(),
                        policy.highlight(), eligibilityByPolicyId.get(policy.policyId())))
                .toList();
        if (cards.isEmpty()) {
            return null;
        }
        return new ChatBlockResponse(POLICY_GROUP, null, block.title(), null, cards, null, null, null, null, null,
                null);
    }

    private ChatBlockResponse comparison(ChatAnswerBlock block, Map<Long, PolicySummaryResponse> summaries) {
        List<Integer> keep = new ArrayList<>();
        for (int index = 0; index < block.policyIds().size(); index++) {
            if (summaries.containsKey(block.policyIds().get(index))) {
                keep.add(index);
            }
        }
        if (keep.size() < MIN_COMPARISON_COLUMNS) {
            return null;
        }
        List<ChatBlockResponse.Column> columns = keep.stream()
                .map(index -> {
                    PolicySummaryResponse summary = summaries.get(block.policyIds().get(index));
                    return new ChatBlockResponse.Column(summary.id(), summary.title());
                })
                .toList();
        List<ChatBlockResponse.Row> rows = block.rows().stream()
                .filter(row -> row.values().size() == block.policyIds().size())
                .map(row -> new ChatBlockResponse.Row(row.label(),
                        keep.stream().map(index -> row.values().get(index)).toList()))
                .toList();
        if (rows.isEmpty()) {
            return null;
        }
        return new ChatBlockResponse(COMPARISON, null, block.title(), null, null, columns, rows, null, null, null,
                null);
    }

    /** 자격 확인엔 판정 텍스트 바로 뒤에 조건별 판정을, 신청 방법엔 단계·서류 뒤에 신청·원문 링크를 붙인다. */
    private void addServerBlocks(String responseType, List<ChatBlockResponse> blocks, DetailContext detail) {
        if (ELIGIBILITY.equals(responseType) && !detail.conditions().isEmpty()) {
            int afterFirstText = blocks.isEmpty() || !TEXT.equals(blocks.get(0).type()) ? 0 : 1;
            blocks.add(afterFirstText, ChatBlockResponse.conditions(detail.conditions()));
        }
        if (HOW_TO_APPLY.equals(responseType) && detail.guide() != null) {
            replaceWithGuide(blocks, detail.guide());
        }
        if (HOW_TO_APPLY.equals(responseType) && (!isBlank(detail.applicationUrl()) || !isBlank(detail.sourceUrl()))) {
            int insertAt = blocks.size();
            for (int index = 0; index < blocks.size(); index++) {
                if (STEPS.equals(blocks.get(index).type()) || CHECKLIST.equals(blocks.get(index).type())) {
                    insertAt = index + 1;
                }
            }
            blocks.add(insertAt, ChatBlockResponse.links(blankToNull(detail.applicationUrl()),
                    blankToNull(detail.sourceUrl())));
        }
    }

    /** 신청 방법 답변의 단계·준비 서류를 캐시된 신청 가이드로 바꾼다(AI가 쓴 단계·서류는 버린다). 첫 텍스트 뒤에 둔다. */
    private void replaceWithGuide(List<ChatBlockResponse> blocks, ApplicationGuideResponse guide) {
        blocks.removeIf(block -> STEPS.equals(block.type()) || CHECKLIST.equals(block.type()));
        List<ChatBlockResponse> guideBlocks = new ArrayList<>();
        List<ChatBlockResponse.Step> steps = guide.steps() == null ? List.of() : guide.steps().stream()
                .filter(step -> !isBlank(step.title()) || !isBlank(step.description()))
                .map(step -> new ChatBlockResponse.Step(step.title(), step.description()))
                .toList();
        if (!steps.isEmpty()) {
            guideBlocks.add(new ChatBlockResponse(STEPS, null, "신청 절차", null, null, null, null, steps, null,
                    null, null));
        }
        if (guide.requiredDocuments() != null && !guide.requiredDocuments().isEmpty()) {
            guideBlocks.add(new ChatBlockResponse(CHECKLIST, null, "준비 서류", null, null, null, null, null,
                    guide.requiredDocuments(), null, null));
        }
        int afterFirstText = !blocks.isEmpty() && TEXT.equals(blocks.get(0).type()) ? 1 : 0;
        blocks.addAll(afterFirstText, guideBlocks);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    /**
     * 특정 정책 질문의 SERVER 판정·링크.
     *
     * @param conditions 사용자가 말한 조건으로 판정한 조건별 결과(조건을 말하지 않았으면 빈 목록)
     * @param guide      캐시된 AI 신청 가이드(없으면 null). 있으면 신청 방법 답변의 단계·준비 서류를 이걸로 채운다
     */
    record DetailContext(List<ConditionResult> conditions, String applicationUrl, String sourceUrl,
                         ApplicationGuideResponse guide) {

        DetailContext {
            conditions = conditions != null ? List.copyOf(conditions) : List.of();
        }

        DetailContext(List<ConditionResult> conditions, String applicationUrl, String sourceUrl) {
            this(conditions, applicationUrl, sourceUrl, null);
        }
    }
}
