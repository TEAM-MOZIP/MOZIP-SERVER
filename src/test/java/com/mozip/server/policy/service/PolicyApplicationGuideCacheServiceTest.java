package com.mozip.server.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mozip.server.ai.dto.ApplicationGuideResponse;
import com.mozip.server.ai.dto.ApplicationGuideStep;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyApplicationGuide;
import com.mozip.server.policy.repository.PolicyApplicationGuideRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PolicyApplicationGuideCacheServiceTest {

    private final PolicyRepository policyRepository = mock(PolicyRepository.class);
    private final PolicyApplicationGuideRepository policyApplicationGuideRepository =
            mock(PolicyApplicationGuideRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PolicyApplicationGuideCacheService cacheService = new PolicyApplicationGuideCacheService(
            policyRepository, policyApplicationGuideRepository, objectMapper);

    private final ApplicationGuideResponse guide = new ApplicationGuideResponse(
            List.of(new ApplicationGuideStep(1, "온라인 신청", "홈페이지에서 신청합니다.")), List.of("신청서"));

    @Test
    void 원문_해시가_같으면_저장된_가이드를_돌려준다() throws Exception {
        String hash = PolicyApplicationGuideCacheService.sourceHash("신청 원문", "서류 원문");
        when(policyApplicationGuideRepository.findByPolicyId(1L))
                .thenReturn(Optional.of(cached(objectMapper.writeValueAsString(guide), hash)));

        Optional<ApplicationGuideResponse> found = cacheService.find(1L, hash);

        assertThat(found).contains(guide);
    }

    @Test
    void 원문이_바뀌었으면_저장된_가이드를_쓰지_않는다() throws Exception {
        String oldHash = PolicyApplicationGuideCacheService.sourceHash("예전 원문", "서류 원문");
        String newHash = PolicyApplicationGuideCacheService.sourceHash("바뀐 원문", "서류 원문");
        when(policyApplicationGuideRepository.findByPolicyId(1L))
                .thenReturn(Optional.of(cached(objectMapper.writeValueAsString(guide), oldHash)));

        assertThat(cacheService.find(1L, newHash)).isEmpty();
    }

    @Test
    void 저장된_내용을_읽을_수_없으면_empty를_돌려준다() {
        String hash = PolicyApplicationGuideCacheService.sourceHash("신청 원문", null);
        when(policyApplicationGuideRepository.findByPolicyId(1L)).thenReturn(Optional.of(cached("{깨진 JSON", hash)));

        assertThat(cacheService.find(1L, hash)).isEmpty();
    }

    @Test
    void 저장된_가이드가_없으면_새로_추가한다() {
        when(policyApplicationGuideRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(policyRepository.getReferenceById(1L)).thenReturn(Policy.builder().title("정책").build());

        cacheService.save(1L, guide, "hash");

        ArgumentCaptor<PolicyApplicationGuide> captor = ArgumentCaptor.forClass(PolicyApplicationGuide.class);
        verify(policyApplicationGuideRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSourceHash()).isEqualTo("hash");
        assertThat(captor.getValue().getContent()).contains("온라인 신청");
    }

    @Test
    void 저장된_가이드가_있으면_내용과_해시를_갱신한다() {
        PolicyApplicationGuide existing = cached("{}", "old");
        when(policyApplicationGuideRepository.findByPolicyId(1L)).thenReturn(Optional.of(existing));

        cacheService.save(1L, guide, "new");

        assertThat(existing.getSourceHash()).isEqualTo("new");
        assertThat(existing.getContent()).contains("온라인 신청");
        verify(policyApplicationGuideRepository, never()).saveAndFlush(any());
    }

    @Test
    void 원문이_다르면_해시가_다르고_같으면_해시가_같다() {
        assertThat(PolicyApplicationGuideCacheService.sourceHash("A", "B"))
                .isEqualTo(PolicyApplicationGuideCacheService.sourceHash("A", "B"))
                .isNotEqualTo(PolicyApplicationGuideCacheService.sourceHash("A", "C"))
                .isNotEqualTo(PolicyApplicationGuideCacheService.sourceHash("AB", ""));
    }

    private PolicyApplicationGuide cached(String content, String sourceHash) {
        return PolicyApplicationGuide.builder()
                .policy(Policy.builder().title("정책").build())
                .content(content)
                .sourceHash(sourceHash)
                .build();
    }
}
