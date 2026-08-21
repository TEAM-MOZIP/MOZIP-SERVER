package com.mozip.server.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mozip.server.chat.domain.ChatCondition;
import com.mozip.server.chat.dto.ChatPolicyMatchResult;
import com.mozip.server.chat.evaluator.ChatConditionEvaluator;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyRegion;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRegionRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import com.mozip.server.region.entity.Region;
import com.mozip.server.region.repository.RegionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatPolicySearchServiceTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicyEligibilityRepository policyEligibilityRepository;

    @Mock
    private PolicyRegionRepository policyRegionRepository;

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private ChatConditionEvaluator chatConditionEvaluator;

    @InjectMocks
    private ChatPolicySearchService chatPolicySearchService;

    private final ChatCondition condition = new ChatCondition(28, null, null, null, null, null);

    @Test
    void 정책이_없으면_빈_리스트를_반환하고_다른_레포지토리는_호출하지_않는다() {
        when(policyRepository.findAll()).thenReturn(List.of());

        List<ChatPolicyMatchResult> results = chatPolicySearchService.search(condition);

        assertThat(results).isEmpty();
        verify(policyEligibilityRepository, never()).findByPolicyIdIn(anyList());
        verify(policyRegionRepository, never()).findByPolicyIdIn(anyList());
        verify(regionRepository, never()).findById(any());
    }

    @Test
    void REGIONAL_정책이_하나도_없으면_지역_레포지토리를_호출하지_않는다() {
        Policy nationalPolicy = policy(1L, RegionScope.NATIONAL);
        when(policyRepository.findAll()).thenReturn(List.of(nationalPolicy));
        when(policyEligibilityRepository.findByPolicyIdIn(anyList())).thenReturn(List.of());
        when(chatConditionEvaluator.evaluate(any(), any(), anyList(), isNull(), isNull()))
                .thenReturn(new PolicyEligibilityResult(EligibilityStatus.NEEDS_REVIEW, "테스트", List.of()));

        chatPolicySearchService.search(condition);

        verify(policyRegionRepository, never()).findByPolicyIdIn(anyList());
    }

    @Test
    void 조건에_regionId가_없으면_region_레포지토리를_호출하지_않는다() {
        Policy nationalPolicy = policy(1L, RegionScope.NATIONAL);
        when(policyRepository.findAll()).thenReturn(List.of(nationalPolicy));
        when(policyEligibilityRepository.findByPolicyIdIn(anyList())).thenReturn(List.of());
        when(chatConditionEvaluator.evaluate(any(), any(), anyList(), isNull(), isNull()))
                .thenReturn(new PolicyEligibilityResult(EligibilityStatus.NEEDS_REVIEW, "테스트", List.of()));

        chatPolicySearchService.search(condition);

        verify(regionRepository, never()).findById(any());
    }

    @Test
    void 조건에_regionId가_있으면_정책_개수와_무관하게_region_조회는_한_번만_수행된다() {
        Region seoul = region(10L, "SEOUL");
        ChatCondition conditionWithRegion = new ChatCondition(28, seoul.getId(), null, null, null, null);
        Policy policy1 = policy(1L, RegionScope.NATIONAL);
        Policy policy2 = policy(2L, RegionScope.NATIONAL);
        Policy policy3 = policy(3L, RegionScope.NATIONAL);

        when(policyRepository.findAll()).thenReturn(List.of(policy1, policy2, policy3));
        when(policyEligibilityRepository.findByPolicyIdIn(anyList())).thenReturn(List.of());
        when(regionRepository.findById(seoul.getId())).thenReturn(Optional.of(seoul));
        when(chatConditionEvaluator.evaluate(any(), any(), anyList(), isNull(), any()))
                .thenReturn(new PolicyEligibilityResult(EligibilityStatus.NEEDS_REVIEW, "테스트", List.of()));

        chatPolicySearchService.search(conditionWithRegion);

        verify(regionRepository, times(1)).findById(seoul.getId());
        verify(chatConditionEvaluator, times(3)).evaluate(eq(conditionWithRegion), any(), anyList(), isNull(), eq(seoul));
    }

    @Test
    void Eligibility가_없는_정책은_evaluator에_null로_전달된다() {
        Policy nationalPolicy = policy(1L, RegionScope.NATIONAL);
        when(policyRepository.findAll()).thenReturn(List.of(nationalPolicy));
        when(policyEligibilityRepository.findByPolicyIdIn(anyList())).thenReturn(List.of());
        PolicyEligibilityResult mockResult = new PolicyEligibilityResult(EligibilityStatus.NEEDS_REVIEW, "테스트", List.of());
        when(chatConditionEvaluator.evaluate(eq(condition), eq(nationalPolicy), eq(List.of()), isNull(), isNull()))
                .thenReturn(mockResult);

        List<ChatPolicyMatchResult> results = chatPolicySearchService.search(condition);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).policy()).isEqualTo(nationalPolicy);
        assertThat(results.get(0).eligibilityResult()).isEqualTo(mockResult);
    }

    @Test
    void 지역_정책의_region_id_목록이_evaluator에_그대로_전달되고_결과가_정책별로_매핑된다() {
        Policy nationalPolicy = policy(1L, RegionScope.NATIONAL);
        Policy regionalPolicy = policy(2L, RegionScope.REGIONAL);
        Region seoul = region(10L, "SEOUL");
        PolicyRegion policyRegion = PolicyRegion.builder().policy(regionalPolicy).region(seoul).build();
        PolicyEligibility eligibility = PolicyEligibility.builder().policy(regionalPolicy).build();

        when(policyRepository.findAll()).thenReturn(List.of(nationalPolicy, regionalPolicy));
        when(policyEligibilityRepository.findByPolicyIdIn(anyList())).thenReturn(List.of(eligibility));
        when(policyRegionRepository.findByPolicyIdIn(List.of(regionalPolicy.getId()))).thenReturn(List.of(policyRegion));

        PolicyEligibilityResult nationalResult = new PolicyEligibilityResult(EligibilityStatus.ELIGIBLE, "전국", List.of());
        PolicyEligibilityResult regionalResult = new PolicyEligibilityResult(EligibilityStatus.INELIGIBLE, "지역", List.of());
        when(chatConditionEvaluator.evaluate(eq(condition), eq(nationalPolicy), eq(List.of()), isNull(), isNull()))
                .thenReturn(nationalResult);
        when(chatConditionEvaluator.evaluate(eq(condition), eq(regionalPolicy), anyList(), eq(eligibility), isNull()))
                .thenReturn(regionalResult);

        List<ChatPolicyMatchResult> results = chatPolicySearchService.search(condition);

        assertThat(results).hasSize(2);
        assertThat(results).anyMatch(r -> r.policy().equals(nationalPolicy) && r.eligibilityResult().equals(nationalResult));
        assertThat(results).anyMatch(r -> r.policy().equals(regionalPolicy) && r.eligibilityResult().equals(regionalResult));

        ArgumentCaptor<List<Long>> regionIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatConditionEvaluator)
                .evaluate(eq(condition), eq(regionalPolicy), regionIdsCaptor.capture(), eq(eligibility), isNull());
        assertThat(regionIdsCaptor.getValue()).containsExactly(seoul.getId());
    }

    private Policy policy(Long id, RegionScope regionScope) {
        Policy policy = Policy.builder()
                .title("테스트 정책 " + id)
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(regionScope)
                .status(PolicyStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }

    private Region region(Long id, String code) {
        Region region = Region.builder().code(code).name(code).build();
        ReflectionTestUtils.setField(region, "id", id);
        return region;
    }
}
