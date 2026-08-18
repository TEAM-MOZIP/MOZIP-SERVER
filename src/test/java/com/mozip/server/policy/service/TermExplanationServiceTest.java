package com.mozip.server.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mozip.server.ai.service.PolicyTermExplanationService;
import com.mozip.server.policy.dto.TermExplanationRequest;
import com.mozip.server.policy.dto.TermExplanationResponse;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyRepository;
import org.junit.jupiter.api.Test;

class TermExplanationServiceTest {

    private final PolicyRepository policyRepository = mock(PolicyRepository.class);
    private final PolicyTermExplanationService policyTermExplanationService = mock(PolicyTermExplanationService.class);
    private final TermExplanationService termExplanationService =
            new TermExplanationService(policyRepository, policyTermExplanationService);

    @Test
    void 존재하지_않는_정책이면_PolicyNotFoundException을_던진다() {
        when(policyRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> termExplanationService.explain(999L, request()))
                .isInstanceOf(PolicyNotFoundException.class);
    }

    @Test
    void 존재하는_정책이면_ai_서비스에_위임하고_결과를_반환한다() {
        when(policyRepository.existsById(1L)).thenReturn(true);
        TermExplanationRequest request = request();
        TermExplanationResponse expected = new TermExplanationResponse("설명 문장");
        when(policyTermExplanationService.explain(request)).thenReturn(expected);

        TermExplanationResponse response = termExplanationService.explain(1L, request);

        assertThat(response).isEqualTo(expected);
        verify(policyTermExplanationService).explain(request);
    }

    @Test
    void 정책이_없으면_ai_서비스를_호출하지_않는다() {
        when(policyRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> termExplanationService.explain(999L, request()));

        verify(policyTermExplanationService, org.mockito.Mockito.never()).explain(any());
    }

    private TermExplanationRequest request() {
        return new TermExplanationRequest("기준중위소득 120%", "본 사업은 기준중위소득 120% 이하의 청년을 대상으로 합니다.");
    }
}
