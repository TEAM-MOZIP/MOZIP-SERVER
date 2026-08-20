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
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.service.PolicySummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PolicySummaryController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class PolicySummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private PolicySummaryService policySummaryService;

    @Test
    void 인증_없이_요청하면_401이다() throws Exception {
        mockMvc.perform(get("/api/policies/1/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증된_사용자는_정책_요약을_받는다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        when(policySummaryService.getSummary(1L)).thenReturn("국민취업지원제도는 취업지원서비스와 소득지원을 결합한 제도입니다.");

        mockMvc.perform(get("/api/policies/1/summary")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyId").value(1))
                .andExpect(jsonPath("$.summary").value("국민취업지원제도는 취업지원서비스와 소득지원을 결합한 제도입니다."));
    }

    @Test
    void 존재하지_않는_정책이면_404다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        when(policySummaryService.getSummary(eq(999L)))
                .thenThrow(new PolicyNotFoundException(999L));

        mockMvc.perform(get("/api/policies/999/summary")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));
    }
}
