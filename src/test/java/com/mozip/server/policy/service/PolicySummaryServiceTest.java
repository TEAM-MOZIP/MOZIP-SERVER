package com.mozip.server.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mozip.server.ai.service.PolicySummaryGenerationService;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.PolicySummary;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.policy.repository.PolicySummaryRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class PolicySummaryServiceTest {

    private final PolicyRepository policyRepository = mock(PolicyRepository.class);
    private final PolicySummaryRepository policySummaryRepository = mock(PolicySummaryRepository.class);
    private final PolicySummaryGenerationService policySummaryGenerationService = mock(PolicySummaryGenerationService.class);
    private final PolicySummaryPersistenceService policySummaryPersistenceService = mock(PolicySummaryPersistenceService.class);

    private final PolicySummaryService policySummaryService = new PolicySummaryService(
            policyRepository, policySummaryRepository, policySummaryGenerationService, policySummaryPersistenceService);

    @Test
    void 존재하지_않는_정책이면_PolicyNotFoundException을_던진다() {
        when(policyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policySummaryService.getSummary(999L))
                .isInstanceOf(PolicyNotFoundException.class);
    }

    @Test
    void source가_전부_없으면_AI와_cache를_전혀_사용하지_않고_Policy_summary를_반환한다() {
        Policy policy = policy(1L, "헤드라인 요약", null, null, null);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));

        String result = policySummaryService.getSummary(1L);

        assertThat(result).isEqualTo("헤드라인 요약");
        verify(policySummaryRepository, never()).findByPolicyId(anyLong());
        verify(policySummaryGenerationService, never()).generate(any(), any(), any(), any());
        verify(policySummaryPersistenceService, never()).insertNew(anyLong(), anyString(), anyString());
        verify(policySummaryPersistenceService, never()).updateExisting(anyLong(), anyString(), anyString());
    }

    @Test
    void cache_hit이면_AI를_호출하지_않고_기존_content를_반환한다() {
        Policy policy = policy(1L, "헤드라인", "설명", "대상", "혜택");
        String currentHash = computeHash(policy);
        PolicySummary existing = existingSummary(2L, "캐시된 요약", currentHash);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policySummaryRepository.findByPolicyId(1L)).thenReturn(Optional.of(existing));

        String result = policySummaryService.getSummary(1L);

        assertThat(result).isEqualTo("캐시된 요약");
        verify(policySummaryGenerationService, never()).generate(any(), any(), any(), any());
        verify(policySummaryPersistenceService, never()).insertNew(anyLong(), anyString(), anyString());
        verify(policySummaryPersistenceService, never()).updateExisting(anyLong(), anyString(), anyString());
    }

    @Test
    void cache_miss_AI_성공이면_insertNew를_호출하고_생성된_content를_반환한다() {
        Policy policy = policy(1L, "헤드라인", "설명", "대상", "혜택");
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policySummaryRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(policySummaryGenerationService.generate("정책명", "설명", "대상", "혜택")).thenReturn("새로 생성된 요약");
        PolicySummary saved = existingSummary(3L, "새로 생성된 요약", "무관");
        when(policySummaryPersistenceService.insertNew(eq(1L), eq("새로 생성된 요약"), anyString())).thenReturn(saved);

        // policy title은 헬퍼에서 "정책명"으로 고정
        String result = policySummaryService.getSummary(1L);

        assertThat(result).isEqualTo("새로 생성된 요약");
        verify(policySummaryPersistenceService).insertNew(eq(1L), eq("새로 생성된 요약"), anyString());
        verify(policySummaryPersistenceService, never()).updateExisting(anyLong(), anyString(), anyString());
    }

    @Test
    void cache_miss_AI_실패면_insertNew를_호출하지_않고_Policy_summary를_반환한다() {
        Policy policy = policy(1L, "헤드라인", "설명", "대상", "혜택");
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policySummaryRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(policySummaryGenerationService.generate("정책명", "설명", "대상", "혜택")).thenReturn(null);

        String result = policySummaryService.getSummary(1L);

        assertThat(result).isEqualTo("헤드라인");
        verify(policySummaryPersistenceService, never()).insertNew(anyLong(), anyString(), anyString());
    }

    @Test
    void stale_AI_성공이면_updateExisting을_호출한다() {
        Policy policy = policy(1L, "헤드라인", "설명", "대상", "혜택");
        PolicySummary existing = existingSummary(2L, "낡은 요약", "old-hash");
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policySummaryRepository.findByPolicyId(1L)).thenReturn(Optional.of(existing));
        when(policySummaryGenerationService.generate("정책명", "설명", "대상", "혜택")).thenReturn("재생성된 요약");
        PolicySummary updated = existingSummary(2L, "재생성된 요약", "new-hash");
        when(policySummaryPersistenceService.updateExisting(eq(2L), eq("재생성된 요약"), anyString())).thenReturn(updated);

        String result = policySummaryService.getSummary(1L);

        assertThat(result).isEqualTo("재생성된 요약");
        verify(policySummaryPersistenceService).updateExisting(eq(2L), eq("재생성된 요약"), anyString());
        verify(policySummaryPersistenceService, never()).insertNew(anyLong(), anyString(), anyString());
    }

    @Test
    void stale_AI_실패면_기존_stale_content를_반환하지_않고_Policy_summary를_반환한다() {
        Policy policy = policy(1L, "헤드라인", "설명", "대상", "혜택");
        PolicySummary existing = existingSummary(2L, "낡은 요약", "old-hash");
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policySummaryRepository.findByPolicyId(1L)).thenReturn(Optional.of(existing));
        when(policySummaryGenerationService.generate("정책명", "설명", "대상", "혜택")).thenReturn(null);

        String result = policySummaryService.getSummary(1L);

        assertThat(result).isEqualTo("헤드라인").isNotEqualTo("낡은 요약");
        verify(policySummaryPersistenceService, never()).updateExisting(anyLong(), anyString(), anyString());
    }

    @Test
    void Policy_summary가_null이면_public_summary는_null이다() {
        Policy policy = policy(1L, null, null, null, null);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));

        assertThat(policySummaryService.getSummary(1L)).isNull();
    }

    @Test
    void Policy_summary가_공백이면_public_summary는_null이다() {
        Policy policy = policy(1L, "   ", null, null, null);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));

        assertThat(policySummaryService.getSummary(1L)).isNull();
    }

    @Test
    void Policy_summary는_trim하지_않고_그대로_반환한다() {
        Policy policy = policy(1L, "  앞뒤 공백 포함 요약  ", null, null, null);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));

        assertThat(policySummaryService.getSummary(1L)).isEqualTo("  앞뒤 공백 포함 요약  ");
    }

    @Test
    void insert_race에서_winner가_존재하고_hash가_같으면_winner_content를_반환한다() {
        Policy policy = policy(1L, "헤드라인", "설명", "대상", "혜택");
        String currentHash = computeHash(policy);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policySummaryRepository.findByPolicyId(1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingSummary(5L, "승자의 요약", currentHash)));
        when(policySummaryGenerationService.generate("정책명", "설명", "대상", "혜택")).thenReturn("내_요약");
        when(policySummaryPersistenceService.insertNew(eq(1L), eq("내_요약"), anyString()))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        String result = policySummaryService.getSummary(1L);

        assertThat(result).isEqualTo("승자의 요약");
    }

    @Test
    void insert_race에서_winner가_존재하지만_hash가_다르면_Policy_summary로_대체한다() {
        Policy policy = policy(1L, "헤드라인", "설명", "대상", "혜택");
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policySummaryRepository.findByPolicyId(1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingSummary(5L, "다른_해시_요약", "완전히-다른-해시")));
        when(policySummaryGenerationService.generate("정책명", "설명", "대상", "혜택")).thenReturn("내_요약");
        when(policySummaryPersistenceService.insertNew(eq(1L), eq("내_요약"), anyString()))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        String result = policySummaryService.getSummary(1L);

        assertThat(result).isEqualTo("헤드라인");
    }

    @Test
    void insert_race에서_winner가_없으면_원본_예외를_그대로_전파한다() {
        Policy policy = policy(1L, "헤드라인", "설명", "대상", "혜택");
        when(policyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(policySummaryRepository.findByPolicyId(1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(policySummaryGenerationService.generate("정책명", "설명", "대상", "혜택")).thenReturn("내_요약");
        DataIntegrityViolationException original = new DataIntegrityViolationException("not-null violation");
        when(policySummaryPersistenceService.insertNew(eq(1L), eq("내_요약"), anyString()))
                .thenThrow(original);

        assertThatThrownBy(() -> policySummaryService.getSummary(1L))
                .isSameAs(original);
    }

    @Test
    void 개행이_포함된_서로_다른_필드_조합은_서로_다른_해시를_만든다() {
        Policy policyA = policy(1L, null, "A\nB", "C", null);
        Policy policyB = policy(2L, null, "A", "B\nC", null);

        String hashA = computeHash(policyA);
        String hashB = computeHash(policyB);

        assertThat(hashA).isNotEqualTo(hashB);
    }

    private Policy policy(Long id, String summary, String description, String targetDescription, String benefitDescription) {
        Organization organization = Organization.builder().name("테스트기관").build();
        Policy policy = Policy.builder()
                .organization(organization)
                .title("정책명")
                .summary(summary)
                .description(description)
                .targetDescription(targetDescription)
                .benefitDescription(benefitDescription)
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.ALWAYS_OPEN)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }

    private PolicySummary existingSummary(Long id, String content, String sourceHash) {
        PolicySummary summary = PolicySummary.builder().content(content).sourceHash(sourceHash).build();
        ReflectionTestUtils.setField(summary, "id", id);
        return summary;
    }

    private String computeHash(Policy policy) {
        return (String) ReflectionTestUtils.invokeMethod(policySummaryService, "computeSourceHash", policy);
    }
}
