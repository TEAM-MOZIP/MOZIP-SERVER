package com.mozip.server.ai.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.ai.dto.GroundingPolicy;
import com.mozip.server.ai.dto.PolicyDetailGrounding;
import com.mozip.server.chat.dto.ChatPolicyMatchResult;
import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.dto.PolicyAvailabilityResponse;
import com.mozip.server.policy.dto.PolicyDetailResponse;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import java.time.LocalDate;
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
