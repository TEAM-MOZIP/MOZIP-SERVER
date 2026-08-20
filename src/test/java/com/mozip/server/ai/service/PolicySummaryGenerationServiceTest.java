package com.mozip.server.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mozip.server.ai.client.PolicySummaryClient;
import com.mozip.server.ai.dto.PolicySummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class PolicySummaryGenerationServiceTest {

    private final PolicySummaryClient policySummaryClient = mock(PolicySummaryClient.class);
    private final PolicySummaryGenerationService policySummaryGenerationService =
            new PolicySummaryGenerationService(policySummaryClient);

    @Test
    void AI_응답이_성공이면_summary를_그대로_반환한다() {
        when(policySummaryClient.generate(any()))
                .thenReturn(new PolicySummaryResponse("종합된 요약 문단입니다."));

        String result = policySummaryGenerationService.generate("정책명", "설명", "대상", "혜택");

        assertThat(result).isEqualTo("종합된 요약 문단입니다.");
    }

    @Test
    void AI_호출이_RestClientException을_던지면_null을_반환한다() {
        when(policySummaryClient.generate(any())).thenThrow(new ResourceAccessException("연결 실패"));

        String result = policySummaryGenerationService.generate("정책명", "설명", "대상", "혜택");

        assertThat(result).isNull();
    }

    @Test
    void 응답이_null이면_null을_반환한다() {
        when(policySummaryClient.generate(any())).thenReturn(null);

        String result = policySummaryGenerationService.generate("정책명", "설명", "대상", "혜택");

        assertThat(result).isNull();
    }
}
