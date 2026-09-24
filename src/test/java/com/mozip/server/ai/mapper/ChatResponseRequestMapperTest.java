package com.mozip.server.ai.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.ai.dto.GroundingPolicy;
import com.mozip.server.ai.dto.PolicyDetailGrounding;
import com.mozip.server.chat.dto.ChatPolicyMatchResult;
import com.mozip.server.chat.dto.ChatTurn;
import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.dto.PolicyAvailabilityResponse;
import com.mozip.server.policy.dto.PolicyDetailResponse;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyApplicationInfo;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ChatResponseRequestMapperTest {

    @Test
    void ChatPolicyMatchResult_목록을_GroundingPolicy_목록으로_변환한다() {
        Policy policy = policy(1L, "국민취업지원제도", LocalDate.of(2026, 12, 31));
        PolicyEligibilityResult eligibilityResult =
                new PolicyEligibilityResult(EligibilityStatus.ELIGIBLE, "충족", List.of());
        ChatPolicyMatchResult match = new ChatPolicyMatchResult(policy, eligibilityResult);

        List<GroundingPolicy> result = ChatResponseRequestMapper.toGroundingPolicies(List.of(match));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).policyId()).isEqualTo(1L);
        assertThat(result.get(0).title()).isEqualTo("국민취업지원제도");
        assertThat(result.get(0).eligibilityStatus()).isEqualTo(EligibilityStatus.ELIGIBLE);
        assertThat(result.get(0).applicationEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(result.get(0).summary()).isNull();
    }

    @Test
    void GroundingPolicy에_정책_요약을_함께_담고_빈_요약은_null로_변환한다() {
        Policy withSummary = policy(1L, "국민취업지원제도", LocalDate.of(2026, 12, 31));
        ReflectionTestUtils.setField(withSummary, "summary", "저소득 구직자에게 취업지원서비스와 생계지원 제공");
        Policy blankSummary = policy(2L, "청년월세지원", null);
        ReflectionTestUtils.setField(blankSummary, "summary", "  ");
        PolicyEligibilityResult eligibilityResult =
                new PolicyEligibilityResult(EligibilityStatus.ELIGIBLE, "충족", List.of());

        List<GroundingPolicy> result = ChatResponseRequestMapper.toGroundingPolicies(List.of(
                new ChatPolicyMatchResult(withSummary, eligibilityResult),
                new ChatPolicyMatchResult(blankSummary, eligibilityResult)));

        assertThat(result.get(0).summary()).isEqualTo("저소득 구직자에게 취업지원서비스와 생계지원 제공");
        assertThat(result.get(1).summary()).isNull();
    }

    @Test
    void PolicyDetailResponse를_PolicyDetailGrounding으로_변환한다() {
        PolicyDetailResponse detail = new PolicyDetailResponse(
                1L, "국민취업지원제도", "요약 문단", "설명", "만 15세 이상 69세 이하 구직자", "월 50만원 지급",
                "온라인 신청", ApplicationType.ALWAYS, null, null, RegionScope.NATIONAL, PolicyStatus.ALWAYS_OPEN,
                null, "고용노동부", null,
                new PolicyAvailabilityResponse(PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.ALWAYS_OPEN, false),
                false
        );

        PolicyDetailGrounding grounding = ChatResponseRequestMapper.toPolicyDetailGrounding(detail);

        assertThat(grounding.title()).isEqualTo("국민취업지원제도");
        assertThat(grounding.summary()).isEqualTo("요약 문단");
        assertThat(grounding.eligibility()).isEqualTo("만 15세 이상 69세 이하 구직자");
        assertThat(grounding.applicationPeriod()).isEqualTo("상시 신청 가능");
        assertThat(grounding.organization()).isEqualTo("고용노동부");
    }

    @Test
    void 신청_정보가_있으면_지원내용_신청방법_준비서류_문의처_신청주소까지_담는다() {
        PolicyDetailResponse detail = new PolicyDetailResponse(
                1L, "국민취업지원제도", "요약 문단", "설명", "만 15세 이상 69세 이하 구직자", "월 50만원 지급",
                "온라인 신청", ApplicationType.ALWAYS, null, null, RegionScope.NATIONAL, PolicyStatus.ALWAYS_OPEN,
                null, "고용노동부", null,
                new PolicyAvailabilityResponse(PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.ALWAYS_OPEN, false),
                false
        );
        PolicyApplicationInfo info = PolicyApplicationInfo.builder()
                .applicationProcedure("고용24에서 온라인 신청")
                .requiredDocumentsText("신분증, 소득 증빙")
                .contactInfo("고용노동부 1350")
                .applicationUrl("https://www.work24.go.kr")
                .sourceUrl("https://www.gov.kr")
                .verifiedAt(LocalDateTime.of(2026, 9, 23, 0, 0))
                .build();

        PolicyDetailGrounding grounding = ChatResponseRequestMapper.toPolicyDetailGrounding(detail, info);

        assertThat(grounding.benefit()).isEqualTo("월 50만원 지급");
        assertThat(grounding.applicationMethod()).isEqualTo("고용24에서 온라인 신청");
        assertThat(grounding.requiredDocuments()).isEqualTo("신분증, 소득 증빙");
        assertThat(grounding.contact()).isEqualTo("고용노동부 1350");
        assertThat(grounding.applicationUrl()).isEqualTo("https://www.work24.go.kr");
    }

    @Test
    void 신청_정보가_없으면_신청방법은_정책의_신청방법을_쓰고_나머지는_null이다() {
        PolicyDetailResponse detail = new PolicyDetailResponse(
                1L, "국민취업지원제도", "요약 문단", "설명", "대상", "월 50만원 지급",
                "온라인 신청", ApplicationType.ALWAYS, null, null, RegionScope.NATIONAL, PolicyStatus.ALWAYS_OPEN,
                null, "고용노동부", null,
                new PolicyAvailabilityResponse(PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.ALWAYS_OPEN, false),
                false
        );

        PolicyDetailGrounding grounding = ChatResponseRequestMapper.toPolicyDetailGrounding(detail, null);

        assertThat(grounding.applicationMethod()).isEqualTo("온라인 신청");
        assertThat(grounding.requiredDocuments()).isNull();
        assertThat(grounding.contact()).isNull();
        assertThat(grounding.applicationUrl()).isNull();
    }

    @Test
    void PERIOD_정책은_시작일과_종료일을_조합한_문자열로_변환된다() {
        PolicyDetailResponse detail = new PolicyDetailResponse(
                1L, "서울시 청년월세지원", null, "설명", null, "월 20만원 지급",
                "온라인 신청", ApplicationType.PERIOD, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                RegionScope.REGIONAL, PolicyStatus.OPEN, null, "서울시", null,
                new PolicyAvailabilityResponse(PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.WITHIN_APPLICATION_PERIOD, false),
                false
        );

        PolicyDetailGrounding grounding = ChatResponseRequestMapper.toPolicyDetailGrounding(detail);

        assertThat(grounding.applicationPeriod()).isEqualTo("2026-01-01 ~ 2026-03-31");
        assertThat(grounding.summary()).isEqualTo("설명");
        assertThat(grounding.eligibility()).isEqualTo("자격 조건 정보가 등록되지 않음");
    }

    @Test
    void chat_dto_ChatTurn_목록을_ai_dto_ChatTurn_목록으로_순서를_유지한_채_변환한다() {
        List<ChatTurn> history = List.of(
                new ChatTurn("국민취업지원제도 알려줘", "국민취업지원제도는 ~ 제도입니다."),
                new ChatTurn("신청 기간은?", "2026-01-01 ~ 2026-12-31입니다."));

        List<com.mozip.server.ai.dto.ChatTurn> result = ChatResponseRequestMapper.toAiChatTurns(history);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).message()).isEqualTo("국민취업지원제도 알려줘");
        assertThat(result.get(0).reply()).isEqualTo("국민취업지원제도는 ~ 제도입니다.");
        assertThat(result.get(1).message()).isEqualTo("신청 기간은?");
        assertThat(result.get(1).reply()).isEqualTo("2026-01-01 ~ 2026-12-31입니다.");
    }

    @Test
    void history는_최근_턴만_남기고_긴_답변은_잘라서_변환한다() {
        List<ChatTurn> history = new java.util.ArrayList<>();
        for (int i = 1; i <= ChatResponseRequestMapper.MAX_HISTORY_TURNS + 2; i++) {
            history.add(new ChatTurn("질문" + i, "답변" + i));
        }
        String longReply = "가".repeat(ChatResponseRequestMapper.MAX_REPLY_LENGTH + 100);
        history.add(new ChatTurn("마지막 질문", longReply));

        List<com.mozip.server.ai.dto.ChatTurn> result = ChatResponseRequestMapper.toAiChatTurns(history);

        assertThat(result).hasSize(ChatResponseRequestMapper.MAX_HISTORY_TURNS);
        assertThat(result.get(result.size() - 1).message()).isEqualTo("마지막 질문");
        assertThat(result.get(result.size() - 1).reply())
                .startsWith("가".repeat(ChatResponseRequestMapper.MAX_REPLY_LENGTH))
                .endsWith("…(생략)")
                .hasSize(ChatResponseRequestMapper.MAX_REPLY_LENGTH + "…(생략)".length());
    }

    @Test
    void 빈_history는_빈_리스트로_변환된다() {
        List<com.mozip.server.ai.dto.ChatTurn> result = ChatResponseRequestMapper.toAiChatTurns(List.of());

        assertThat(result).isEmpty();
    }

    private Policy policy(Long id, String title, LocalDate applicationEndDate) {
        Policy policy = Policy.builder()
                .title(title)
                .applicationType(ApplicationType.PERIOD)
                .applicationEndDate(applicationEndDate)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }
}
