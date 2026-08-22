package com.mozip.server.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mozip.server.ai.client.ConditionExtractionClient;
import com.mozip.server.ai.dto.ConditionExtractionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class ConditionExtractionServiceTest {

    private final ConditionExtractionClient conditionExtractionClient = mock(ConditionExtractionClient.class);
    private final ConditionExtractionService conditionExtractionService =
            new ConditionExtractionService(conditionExtractionClient);

    @Test
    void AI_응답이_성공이면_그대로_반환한다() {
        ConditionExtractionResponse response =
                new ConditionExtractionResponse(null, 25, null, null, null, null, null, java.util.List.of());
        when(conditionExtractionClient.extract(any())).thenReturn(response);

        ConditionExtractionResponse result = conditionExtractionService.extract("25살인데 받을 정책 있어?");

        assertThat(result).isSameAs(response);
    }

    @Test
    void AI_호출이_RestClientException을_던지면_null을_반환한다() {
        when(conditionExtractionClient.extract(any())).thenThrow(new ResourceAccessException("연결 실패"));

        ConditionExtractionResponse result = conditionExtractionService.extract("서울 사는 취준생이 받을 정책 있어?");

        assertThat(result).isNull();
    }
}
