package com.mozip.server.policy.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mozip.server.auth.config.CustomAccessDeniedHandler;
import com.mozip.server.auth.config.CustomAuthenticationEntryPoint;
import com.mozip.server.auth.config.SecurityConfig;
import com.mozip.server.auth.jwt.JwtTokenProvider;
import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.dto.ApplicationGuideResponse;
import com.mozip.server.policy.dto.ApplicationGuideStepResponse;
import com.mozip.server.policy.dto.PolicyAvailabilityResponse;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.service.ApplicationGuideService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ApplicationGuideController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class ApplicationGuideControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ApplicationGuideService applicationGuideService;

    @Test
    void 인증_없이_요청하면_401이다() throws Exception {
        mockMvc.perform(get("/api/policies/1/application-guide"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증된_사용자는_신청_가이드를_받는다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        ApplicationGuideResponse response = new ApplicationGuideResponse(
                1L, null, "만 15세 이상 청년",
                ApplicationType.ALWAYS, null, null,
                new PolicyAvailabilityResponse(PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.ALWAYS_OPEN, false),
                List.of(new ApplicationGuideStepResponse(1, "신청 방법", "온라인 신청")),
                List.of(),
                "https://www.work24.go.kr",
                "1350",
                null
        );
        when(applicationGuideService.getApplicationGuide(1L)).thenReturn(response);

        mockMvc.perform(get("/api/policies/1/application-guide")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyId").value(1))
                .andExpect(jsonPath("$.steps[0].title").value("신청 방법"))
                .andExpect(jsonPath("$.applicationUrl").value("https://www.work24.go.kr"));
    }

    @Test
    void 존재하지_않는_정책이면_404다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        when(applicationGuideService.getApplicationGuide(eq(999L)))
                .thenThrow(new PolicyNotFoundException(999L));

        mockMvc.perform(get("/api/policies/999/application-guide")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));
    }
}
