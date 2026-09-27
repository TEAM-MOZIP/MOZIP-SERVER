package com.mozip.server.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mozip.server.ai.dto.ChatAnswerBlock;
import com.mozip.server.ai.dto.ChatResponseResponse;
import com.mozip.server.chat.dto.ChatBlockResponse;
import com.mozip.server.chat.dto.ChatResponse;
import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.dto.PolicyAvailabilityResponse;
import com.mozip.server.policy.dto.PolicySummaryResponse;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.service.PolicyService;
import com.mozip.server.recommendation.domain.ConditionResult;
import com.mozip.server.recommendation.domain.ConditionStatus;
import com.mozip.server.recommendation.domain.ConditionType;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatAnswerAssemblerTest {

    private final PolicyService policyService = mock(PolicyService.class);
    private final ChatAnswerAssembler assembler = new ChatAnswerAssembler(policyService);

    @Test
    void 정책_카드에_목록_카드_정보와_추천_이유를_채우고_없는_정책은_버린다() {
        when(policyService.getSummariesByIds(anyList())).thenReturn(Map.of(1L, summary(1L, "월세 지원")));
        ChatResponseResponse answer = new ChatResponseResponse("요약", "RECOMMEND", List.of(
                ChatAnswerBlock.text("TEXT", "조건에 맞는 정책을 골랐어요."),
                new ChatAnswerBlock("POLICY_GROUP", null, "딱 맞는 정책", null, List.of(
                        new ChatAnswerBlock.Policy(1L, "무주택 청년 대상이에요", "월 20만 원"),
                        new ChatAnswerBlock.Policy(99L, "없는 정책", null)),
                        null, null, null, null)),
                List.of("신청 방법 알려줘"));

        ChatResponse response = assembler.assemble(answer, Map.of(1L, EligibilityStatus.ELIGIBLE), null, List.of(),
                List.of());

        assertThat(response.responseType()).isEqualTo("RECOMMEND");
        assertThat(response.blocks()).extracting(ChatBlockResponse::type).containsExactly("TEXT", "POLICY_GROUP");
        ChatBlockResponse group = response.blocks().get(1);
        assertThat(group.policies()).hasSize(1);
        assertThat(group.policies().get(0).title()).isEqualTo("월세 지원");
        assertThat(group.policies().get(0).reason()).isEqualTo("무주택 청년 대상이에요");
        assertThat(group.policies().get(0).highlight()).isEqualTo("월 20만 원");
        assertThat(group.policies().get(0).eligibilityStatus()).isEqualTo(EligibilityStatus.ELIGIBLE);
        assertThat(response.followUps()).containsExactly("신청 방법 알려줘");
    }

    @Test
    void 비교표_열에_정책_제목을_채우고_없는_정책의_열은_뺀다() {
        when(policyService.getSummariesByIds(anyList())).thenReturn(Map.of(
                1L, summary(1L, "월세 지원"), 2L, summary(2L, "전세 대출")));
        ChatResponseResponse answer = new ChatResponseResponse("비교", "COMPARE", List.of(
                new ChatAnswerBlock("COMPARISON", null, null, null, null, List.of(1L, 99L, 2L),
                        List.of(new ChatAnswerBlock.Row("지원 방식", List.of("현금", "?", "대출"))), null, null)),
                List.of());

        ChatResponse response = assembler.assemble(answer, Map.of(), null, List.of(), List.of());

        ChatBlockResponse comparison = response.blocks().get(0);
        assertThat(comparison.columns()).extracting(ChatBlockResponse.Column::title)
                .containsExactly("월세 지원", "전세 대출");
        assertThat(comparison.rows().get(0).values()).containsExactly("현금", "대출");
    }

    @Test
    void 자격_확인_답변에는_판정_텍스트_뒤에_조건별_판정을_붙인다() {
        when(policyService.getSummariesByIds(anyList())).thenReturn(Map.of());
        ConditionResult age = new ConditionResult(ConditionType.AGE, ConditionStatus.MATCHED, "만 25세");
        ChatResponseResponse answer = new ChatResponseResponse("거의 해당돼요", "ELIGIBILITY", List.of(
                ChatAnswerBlock.text("TEXT", "거의 해당돼요."),
                new ChatAnswerBlock("QUICK_REPLIES", "소득을 알려주세요", null, null, null, null, null, null,
                        List.of("중위소득 60% 이하"))),
                List.of());

        ChatResponse response = assembler.assemble(answer, Map.of(),
                new ChatAnswerAssembler.DetailContext(List.of(age), null, null), List.of(), List.of());

        assertThat(response.blocks()).extracting(ChatBlockResponse::type)
                .containsExactly("TEXT", "CONDITIONS", "QUICK_REPLIES");
        assertThat(response.blocks().get(1).conditions()).containsExactly(age);
    }

    @Test
    void 신청_방법_답변에는_단계와_서류_뒤에_신청_원문_링크를_붙인다() {
        when(policyService.getSummariesByIds(anyList())).thenReturn(Map.of());
        ChatResponseResponse answer = new ChatResponseResponse("온라인 신청", "HOW_TO_APPLY", List.of(
                ChatAnswerBlock.text("TEXT", "온라인으로 신청해요."),
                new ChatAnswerBlock("STEPS", null, "신청 절차", null, null, null, null,
                        List.of(new ChatAnswerBlock.Step("복지로 접속", null)), null),
                new ChatAnswerBlock("CHECKLIST", null, "준비 서류", null, null, null, null, null,
                        List.of("임대차계약서"))),
                List.of());

        ChatResponse response = assembler.assemble(answer, Map.of(),
                new ChatAnswerAssembler.DetailContext(List.of(), "https://apply", " "), List.of(), List.of());

        assertThat(response.blocks()).extracting(ChatBlockResponse::type)
                .containsExactly("TEXT", "STEPS", "CHECKLIST", "LINKS");
        assertThat(response.blocks().get(3).links().applicationUrl()).isEqualTo("https://apply");
        assertThat(response.blocks().get(3).links().sourceUrl()).isNull();
    }

    @Test
    void 캐시된_신청_가이드가_있으면_AI가_쓴_단계_대신_가이드로_채운다() {
        when(policyService.getSummariesByIds(anyList())).thenReturn(Map.of());
        ChatResponseResponse answer = new ChatResponseResponse("온라인 신청", "HOW_TO_APPLY", List.of(
                ChatAnswerBlock.text("TEXT", "온라인으로 신청해요."),
                new ChatAnswerBlock("STEPS", null, "신청 절차", null, null, null, null,
                        List.of(new ChatAnswerBlock.Step("AI가 쓴 단계", null)), null)),
                List.of());
        com.mozip.server.ai.dto.ApplicationGuideResponse guide = new com.mozip.server.ai.dto.ApplicationGuideResponse(
                List.of(new com.mozip.server.ai.dto.ApplicationGuideStep(1, "복지로 접속", "로그인해요")),
                List.of("임대차계약서"));

        ChatResponse response = assembler.assemble(answer, Map.of(),
                new ChatAnswerAssembler.DetailContext(List.of(), null, "https://source", guide), List.of(),
                List.of());

        assertThat(response.blocks()).extracting(ChatBlockResponse::type)
                .containsExactly("TEXT", "STEPS", "CHECKLIST", "LINKS");
        assertThat(response.blocks().get(1).steps()).extracting(ChatBlockResponse.Step::title)
                .containsExactly("복지로 접속");
        assertThat(response.blocks().get(2).items()).containsExactly("임대차계약서");
    }

    @Test
    void 블록이_없으면_요약_문장을_텍스트_블록으로_보여준다() {
        when(policyService.getSummariesByIds(anyList())).thenReturn(Map.of());

        ChatResponse response = assembler.assemble(new ChatResponseResponse("안녕하세요!"), Map.of(), null, List.of(),
                List.of());

        assertThat(response.responseType()).isEqualTo("GENERAL");
        assertThat(response.blocks()).extracting(ChatBlockResponse::text).containsExactly("안녕하세요!");
    }

    private PolicySummaryResponse summary(Long id, String title) {
        return new PolicySummaryResponse(id, title, "요약", "기관", ApplicationType.ALWAYS, null, null,
                RegionScope.NATIONAL, PolicyStatus.ALWAYS_OPEN,
                new PolicyAvailabilityResponse(PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.ALWAYS_OPEN, false));
    }
}
