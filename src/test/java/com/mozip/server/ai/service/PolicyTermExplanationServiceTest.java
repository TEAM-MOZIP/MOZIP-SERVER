package com.mozip.server.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mozip.server.ai.client.TermExplanationClient;
import com.mozip.server.ai.dto.TermExplainResponse;
import com.mozip.server.ai.exception.TermExplanationUnavailableException;
import com.mozip.server.policy.dto.TermExplanationRequest;
import com.mozip.server.policy.dto.TermExplanationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class PolicyTermExplanationServiceTest {

    private final TermExplanationClient termExplanationClient = mock(TermExplanationClient.class);
    private final PolicyTermExplanationService policyTermExplanationService =
            new PolicyTermExplanationService(termExplanationClient);

    @Test
    void AI_응답이_성공이면_explanation을_반환한다() {
        when(termExplanationClient.explain(any()))
                .thenReturn(new TermExplainResponse("가구원 수에 따라 정해진 기준소득의 1.2배 이하를 의미합니다."));

        TermExplanationResponse response = policyTermExplanationService.explain(request());

        assertThat(response.explanation()).isEqualTo("가구원 수에 따라 정해진 기준소득의 1.2배 이하를 의미합니다.");
    }

    @Test
    void AI_호출이_RestClientException을_던지면_TermExplanationUnavailableException을_던진다() {
        when(termExplanationClient.explain(any())).thenThrow(new ResourceAccessException("연결 실패"));

        assertThatThrownBy(() -> policyTermExplanationService.explain(request()))
                .isInstanceOf(TermExplanationUnavailableException.class);
    }

    @Test
    void 응답이_null이면_TermExplanationUnavailableException을_던진다() {
        when(termExplanationClient.explain(any())).thenReturn(null);

        assertThatThrownBy(() -> policyTermExplanationService.explain(request()))
                .isInstanceOf(TermExplanationUnavailableException.class);
    }

    @Test
    void explanation이_null이면_TermExplanationUnavailableException을_던진다() {
        when(termExplanationClient.explain(any())).thenReturn(new TermExplainResponse(null));

        assertThatThrownBy(() -> policyTermExplanationService.explain(request()))
                .isInstanceOf(TermExplanationUnavailableException.class);
    }

    @Test
    void explanation이_공백문자열이면_TermExplanationUnavailableException을_던진다() {
        when(termExplanationClient.explain(any())).thenReturn(new TermExplainResponse("   "));

        assertThatThrownBy(() -> policyTermExplanationService.explain(request()))
                .isInstanceOf(TermExplanationUnavailableException.class);
    }

    private TermExplanationRequest request() {
        return new TermExplanationRequest("기준중위소득 120%", "본 사업은 기준중위소득 120% 이하의 청년을 대상으로 합니다.");
    }
}
