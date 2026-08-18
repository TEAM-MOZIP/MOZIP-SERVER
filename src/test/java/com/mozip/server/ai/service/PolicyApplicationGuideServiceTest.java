package com.mozip.server.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mozip.server.ai.client.ApplicationGuideClient;
import com.mozip.server.ai.dto.ApplicationGuideResponse;
import com.mozip.server.ai.dto.ApplicationGuideStep;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class PolicyApplicationGuideServiceTest {

    private final ApplicationGuideClient applicationGuideClient = mock(ApplicationGuideClient.class);
    private final PolicyApplicationGuideService policyApplicationGuideService =
            new PolicyApplicationGuideService(applicationGuideClient);

    @Test
    void AI_응답이_성공이면_steps와_requiredDocuments를_그대로_반환한다() {
        ApplicationGuideResponse aiResponse = new ApplicationGuideResponse(
                List.of(new ApplicationGuideStep(1, "신청", "온라인으로 신청합니다.")),
                List.of("신청서"));
        when(applicationGuideClient.generate(any())).thenReturn(aiResponse);

        ApplicationGuideResponse result = policyApplicationGuideService.generate("고용센터 방문", "취업지원신청서");

        assertThat(result).isEqualTo(aiResponse);
    }

    @Test
    void AI_호출이_RestClientException을_던지면_원본_source_기반_단일_step으로_대체한다() {
        when(applicationGuideClient.generate(any())).thenThrow(new ResourceAccessException("연결 실패"));

        ApplicationGuideResponse result = policyApplicationGuideService.generate("고용센터 방문 또는 온라인 신청",
                "취업지원신청서");

        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).order()).isEqualTo(1);
        assertThat(result.steps().get(0).title()).isEqualTo("신청 절차");
        assertThat(result.steps().get(0).description()).isEqualTo("고용센터 방문 또는 온라인 신청");
        assertThat(result.requiredDocuments()).containsExactly("취업지원신청서");
    }

    @Test
    void AI_실패_시_requiredDocumentsSource가_없으면_빈_배열이다() {
        when(applicationGuideClient.generate(any())).thenThrow(new ResourceAccessException("연결 실패"));

        ApplicationGuideResponse result = policyApplicationGuideService.generate("고용센터 방문", null);

        assertThat(result.requiredDocuments()).isEmpty();
    }

    @Test
    void 응답이_null이면_원본_source_기반으로_대체한다() {
        when(applicationGuideClient.generate(any())).thenReturn(null);

        ApplicationGuideResponse result = policyApplicationGuideService.generate("고용센터 방문", "신청서");

        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).description()).isEqualTo("고용센터 방문");
    }

    @Test
    void steps가_빈_배열이면_원본_source_기반으로_대체한다() {
        when(applicationGuideClient.generate(any())).thenReturn(new ApplicationGuideResponse(List.of(), List.of()));

        ApplicationGuideResponse result = policyApplicationGuideService.generate("고용센터 방문", "신청서");

        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).description()).isEqualTo("고용센터 방문");
    }
}
