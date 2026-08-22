package com.mozip.server.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mozip.server.ai.dto.ChatResponseResponse;
import com.mozip.server.ai.dto.ConditionAxis;
import com.mozip.server.ai.dto.ConditionExtractionResponse;
import com.mozip.server.ai.dto.GroundingPolicy;
import com.mozip.server.ai.dto.UnresolvedCondition;
import com.mozip.server.ai.exception.ChatResponseUnavailableException;
import com.mozip.server.ai.service.ChatResponseGenerationService;
import com.mozip.server.ai.service.ConditionExtractionService;
import com.mozip.server.chat.domain.ChatCondition;
import com.mozip.server.chat.dto.ChatPolicyMatchResult;
import com.mozip.server.chat.dto.ChatRequest;
import com.mozip.server.chat.dto.ChatResponse;
import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.dto.PolicyAvailabilityResponse;
import com.mozip.server.policy.dto.PolicyDetailResponse;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.policy.service.PolicyService;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import com.mozip.server.region.entity.Region;
import com.mozip.server.region.repository.RegionRepository;
import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.Gender;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConditionExtractionService conditionExtractionService;

    @Mock
    private ChatPolicySearchService chatPolicySearchService;

    @Mock
    private ChatResponseGenerationService chatResponseGenerationService;

    @Mock
    private PolicyService policyService;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private RegionRepository regionRepository;

    private ChatService chatService;

    private void setUp() {
        chatService = new ChatService(conditionExtractionService, chatPolicySearchService, chatResponseGenerationService,
                policyService, policyRepository, regionRepository);
    }

    @Test
    void CaseA_actionable_axis가_있으면_조건_기반_탐색을_수행한다() {
        setUp();
        ConditionExtractionResponse extraction = extraction(null, 25, null, null, null, null, null, List.of());
        when(conditionExtractionService.extract("25살인데 받을 정책 있어?")).thenReturn(extraction);
        when(chatPolicySearchService.search(any())).thenReturn(List.of(
                matchResult(1L, "정책A", EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 6, 30))));
        when(chatResponseGenerationService.generate(any(), anyList(), isNull(), anyList())).thenReturn("답변");

        ChatResponse response = chatService.handle(new ChatRequest("25살인데 받을 정책 있어?"));

        assertThat(response.reply()).isEqualTo("답변");
        assertThat(response.matchedPolicies()).hasSize(1);
        assertThat(response.matchedPolicies().get(0).policyId()).isEqualTo(1L);

        ArgumentCaptor<ChatCondition> conditionCaptor = ArgumentCaptor.forClass(ChatCondition.class);
        verify(chatPolicySearchService).search(conditionCaptor.capture());
        assertThat(conditionCaptor.getValue().age()).isEqualTo(25);
        assertThat(conditionCaptor.getValue().regionId()).isNull();
    }

    @Test
    void CaseA_regionCode가_있으면_RegionRepository로_regionId를_변환해_전달한다() {
        setUp();
        Region seoul = region(10L, "SEOUL");
        ConditionExtractionResponse extraction = extraction(null, null, "SEOUL", null, null, null, null, List.of());
        when(conditionExtractionService.extract(any())).thenReturn(extraction);
        when(regionRepository.findByCode("SEOUL")).thenReturn(Optional.of(seoul));
        when(chatPolicySearchService.search(any())).thenReturn(List.of());
        when(chatResponseGenerationService.generate(any(), anyList(), isNull(), anyList())).thenReturn("답변");

        chatService.handle(new ChatRequest("서울 사는 사람이 받을 정책 있어?"));

        ArgumentCaptor<ChatCondition> conditionCaptor = ArgumentCaptor.forClass(ChatCondition.class);
        verify(chatPolicySearchService).search(conditionCaptor.capture());
        assertThat(conditionCaptor.getValue().regionId()).isEqualTo(10L);
    }

    @Test
    void CaseB_actionable_axis가_없고_제목도_매칭되지_않으면_일반_fallback으로_처리한다() {
        setUp();
        ConditionExtractionResponse extraction = extraction(null, null, null, null, null, null, null, List.of());
        when(conditionExtractionService.extract(any())).thenReturn(extraction);
        when(policyRepository.findAll()).thenReturn(List.of(policy(1L, "국민취업지원제도")));
        when(chatResponseGenerationService.generate(any(), eq(List.of()), isNull(), anyList())).thenReturn("일반 답변");

        ChatResponse response = chatService.handle(new ChatRequest("기준중위소득이 뭐야?"));

        assertThat(response.reply()).isEqualTo("일반 답변");
        assertThat(response.matchedPolicies()).isEmpty();
        verify(chatPolicySearchService, never()).search(any());
    }

    @Test
    void CaseC_정책_제목이_메시지에_정확히_하나만_포함되면_해당_정책_상세를_grounding으로_전달한다() {
        setUp();
        ConditionExtractionResponse extraction = extraction(null, null, null, null, null, null, null, List.of());
        when(conditionExtractionService.extract(any())).thenReturn(extraction);
        when(policyRepository.findAll()).thenReturn(List.of(policy(1L, "국민취업지원제도"), policy(2L, "청년월세지원")));
        when(policyService.getPolicyDetail(1L, null)).thenReturn(policyDetail(1L, "국민취업지원제도"));
        when(chatResponseGenerationService.generate(any(), eq(List.of()), any(), anyList())).thenReturn("정책 설명");

        ChatResponse response = chatService.handle(new ChatRequest("국민취업지원제도가 뭐야?"));

        assertThat(response.reply()).isEqualTo("정책 설명");
        verify(policyService).getPolicyDetail(1L, null);
    }

    @Test
    void 제목이_여러_정책에_동시에_매칭되면_모호한_것으로_보아_매칭하지_않는다() {
        setUp();
        ConditionExtractionResponse extraction = extraction(null, null, null, null, null, null, null, List.of());
        when(conditionExtractionService.extract(any())).thenReturn(extraction);
        when(policyRepository.findAll()).thenReturn(List.of(policy(1L, "청년"), policy(2L, "청년월세지원")));
        when(chatResponseGenerationService.generate(any(), eq(List.of()), isNull(), anyList())).thenReturn("일반 답변");

        chatService.handle(new ChatRequest("청년월세지원이 뭐야?"));

        verify(policyService, never()).getPolicyDetail(any(), any());
    }

    @Test
    void gender만_resolve되면_조건_기반_탐색으로_진입하지_않는다() {
        setUp();
        ConditionExtractionResponse extraction = extraction(Gender.FEMALE, null, null, null, null, null, null, List.of());
        when(conditionExtractionService.extract(any())).thenReturn(extraction);
        when(policyRepository.findAll()).thenReturn(List.of());
        when(chatResponseGenerationService.generate(any(), eq(List.of()), isNull(), anyList())).thenReturn("일반 답변");

        chatService.handle(new ChatRequest("저는 여성입니다."));

        verify(chatPolicySearchService, never()).search(any());
    }

    @Test
    void actionable_axis와_unresolvedConditions가_혼재하면_둘_다_응답에_반영된다() {
        setUp();
        UnresolvedCondition unresolved = new UnresolvedCondition(ConditionAxis.EMPLOYMENT_STATUS, "프리랜서");
        ConditionExtractionResponse extraction =
                extraction(null, null, "SEOUL", null, null, null, null, List.of(unresolved));
        when(conditionExtractionService.extract(any())).thenReturn(extraction);
        when(regionRepository.findByCode("SEOUL")).thenReturn(Optional.of(region(10L, "SEOUL")));
        when(chatPolicySearchService.search(any())).thenReturn(List.of());
        when(chatResponseGenerationService.generate(any(), anyList(), isNull(), eq(List.of(unresolved))))
                .thenReturn("답변");

        ChatResponse response = chatService.handle(new ChatRequest("서울 사는 프리랜서가 받을 정책 있어?"));

        assertThat(response.unresolvedConditions()).hasSize(1);
        assertThat(response.unresolvedConditions().get(0).axis()).isEqualTo(ConditionAxis.EMPLOYMENT_STATUS);
        assertThat(response.unresolvedConditions().get(0).rawText()).isEqualTo("프리랜서");
    }

    @Test
    void INELIGIBLE_정책은_grounding과_matchedPolicies에서_제외된다() {
        setUp();
        ConditionExtractionResponse extraction = extraction(null, 15, null, null, null, null, null, List.of());
        when(conditionExtractionService.extract(any())).thenReturn(extraction);
        when(chatPolicySearchService.search(any())).thenReturn(List.of(
                matchResult(1L, "적격 정책", EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 6, 30)),
                matchResult(2L, "부적격 정책", EligibilityStatus.INELIGIBLE, LocalDate.of(2026, 1, 1))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GroundingPolicy>> groundingCaptor = ArgumentCaptor.forClass(List.class);
        when(chatResponseGenerationService.generate(any(), groundingCaptor.capture(), isNull(), anyList()))
                .thenReturn("답변");

        ChatResponse response = chatService.handle(new ChatRequest("15살인데 받을 정책 있어?"));

        assertThat(groundingCaptor.getValue()).hasSize(1);
        assertThat(groundingCaptor.getValue().get(0).policyId()).isEqualTo(1L);
        assertThat(response.matchedPolicies()).hasSize(1);
        assertThat(response.matchedPolicies().get(0).policyId()).isEqualTo(1L);
    }

    @Test
    void 정책이_5개를_초과하면_정렬_후_상위_5개만_grounding으로_전달된다() {
        setUp();
        ConditionExtractionResponse extraction = extraction(null, 25, null, null, null, null, null, List.of());
        when(conditionExtractionService.extract(any())).thenReturn(extraction);
        // ELIGIBLE 4건 + NEEDS_REVIEW 2건 = 6건. top5는 ELIGIBLE 4건 전부(endDate 빠른 순) +
        // NEEDS_REVIEW 중 endDate가 더 빠른 1건(정책1)만 포함되고, 나머지 NEEDS_REVIEW(정책6)는 잘려나간다.
        List<ChatPolicyMatchResult> sixMatches = List.of(
                matchResult(1L, "정책1", EligibilityStatus.NEEDS_REVIEW, LocalDate.of(2026, 1, 1)),
                matchResult(2L, "정책2", EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 6, 30)),
                matchResult(3L, "정책3", EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 3, 31)),
                matchResult(4L, "정책4", EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 5, 31)),
                matchResult(5L, "정책5", EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 2, 28)),
                matchResult(6L, "정책6", EligibilityStatus.NEEDS_REVIEW, LocalDate.of(2026, 4, 30)));
        when(chatPolicySearchService.search(any())).thenReturn(sixMatches);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GroundingPolicy>> groundingCaptor = ArgumentCaptor.forClass(List.class);
        when(chatResponseGenerationService.generate(any(), groundingCaptor.capture(), isNull(), anyList()))
                .thenReturn("답변");

        chatService.handle(new ChatRequest("25살인데 받을 정책 있어?"));

        List<Long> orderedIds = groundingCaptor.getValue().stream().map(GroundingPolicy::policyId).toList();
        assertThat(orderedIds).hasSize(5);
        assertThat(orderedIds).containsExactly(5L, 3L, 4L, 2L, 1L);
    }

    @Test
    void condition_extraction이_null을_반환하면_예외없이_fallback_경로로_진행한다() {
        setUp();
        when(conditionExtractionService.extract(any())).thenReturn(null);
        when(policyRepository.findAll()).thenReturn(List.of());
        when(chatResponseGenerationService.generate(any(), eq(List.of()), isNull(), eq(List.of()))).thenReturn("일반 답변");

        ChatResponse response = chatService.handle(new ChatRequest("아무 조건도 없는 질문"));

        assertThat(response.reply()).isEqualTo("일반 답변");
        verify(chatPolicySearchService, never()).search(any());
    }

    @Test
    void chat_response_생성이_실패하면_ChatResponseUnavailableException이_그대로_전파된다() {
        setUp();
        ConditionExtractionResponse extraction = extraction(null, 25, null, null, null, null, null, List.of());
        when(conditionExtractionService.extract(any())).thenReturn(extraction);
        when(chatPolicySearchService.search(any())).thenReturn(List.of());
        when(chatResponseGenerationService.generate(any(), anyList(), isNull(), anyList()))
                .thenThrow(new ChatResponseUnavailableException());

        assertThatThrownBy(() -> chatService.handle(new ChatRequest("25살인데 받을 정책 있어?")))
                .isInstanceOf(ChatResponseUnavailableException.class);
    }

    private ConditionExtractionResponse extraction(Gender gender, Integer age, String regionCode,
                                                     EmploymentStatus employmentStatus,
                                                     com.mozip.server.user.entity.HouseholdType householdType,
                                                     com.mozip.server.user.entity.IncomeType incomeType,
                                                     Integer incomeValue, List<UnresolvedCondition> unresolvedConditions) {
        return new ConditionExtractionResponse(gender, age, regionCode, employmentStatus, householdType, incomeType,
                incomeValue, unresolvedConditions);
    }

    private ChatPolicyMatchResult matchResult(Long id, String title, EligibilityStatus status, LocalDate applicationEndDate) {
        Policy policy = Policy.builder()
                .title(title)
                .applicationType(ApplicationType.PERIOD)
                .applicationEndDate(applicationEndDate)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return new ChatPolicyMatchResult(policy, new PolicyEligibilityResult(status, "테스트", List.of()));
    }

    private Policy policy(Long id, String title) {
        Policy policy = Policy.builder()
                .title(title)
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.ALWAYS_OPEN)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }

    private Region region(Long id, String code) {
        Region region = Region.builder().code(code).name(code).build();
        ReflectionTestUtils.setField(region, "id", id);
        return region;
    }

    private PolicyDetailResponse policyDetail(Long id, String title) {
        return new PolicyDetailResponse(
                id, title, "요약", "설명", "대상", "혜택", "온라인 신청", ApplicationType.ALWAYS, null, null,
                RegionScope.NATIONAL, PolicyStatus.ALWAYS_OPEN, null, "기관", null,
                new PolicyAvailabilityResponse(PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.ALWAYS_OPEN, false),
                false
        );
    }
}
