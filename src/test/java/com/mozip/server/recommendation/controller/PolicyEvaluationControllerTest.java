package com.mozip.server.recommendation.controller;

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
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.recommendation.domain.ConditionResult;
import com.mozip.server.recommendation.domain.ConditionStatus;
import com.mozip.server.recommendation.domain.ConditionType;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.dto.PolicyEvaluationResponse;
import com.mozip.server.recommendation.service.PolicyEvaluationService;
import com.mozip.server.user.exception.UserProfileNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PolicyEvaluationController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class PolicyEvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private PolicyEvaluationService policyEvaluationService;

    @Test
    void 인증_없이_조회하면_401이다() throws Exception {
        mockMvc.perform(get("/api/recommendations/policies/1/evaluation"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 존재하지_않는_정책이면_404다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        when(policyEvaluationService.evaluate(eq(1L), eq(999L)))
                .thenThrow(new PolicyNotFoundException(999L));

        mockMvc.perform(get("/api/recommendations/policies/999/evaluation")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));
    }

    @Test
    void 사용자_프로필이_없으면_404다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        when(policyEvaluationService.evaluate(eq(1L), eq(1L)))
                .thenThrow(new UserProfileNotFoundException(1L));

        mockMvc.perform(get("/api/recommendations/policies/1/evaluation")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_PROFILE_NOT_FOUND"));
    }

    @Test
    void 인증된_사용자는_정책_판정_결과를_조회한다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        PolicyEvaluationResponse response = new PolicyEvaluationResponse(
                1L,
                new PolicyEvaluationResponse.EligibilityResponse(
                        EligibilityStatus.ELIGIBLE,
                        "모든 자동 판정 조건을 충족했습니다.",
                        List.of(new ConditionResult(ConditionType.AGE, ConditionStatus.MATCHED, "연령 조건을 충족합니다."))
                ),
                new PolicyEvaluationResponse.AvailabilityResponse(
                        PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.WITHIN_APPLICATION_PERIOD
                )
        );
        when(policyEvaluationService.evaluate(eq(1L), eq(1L))).thenReturn(response);

        mockMvc.perform(get("/api/recommendations/policies/1/evaluation")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyId").value(1))
                .andExpect(jsonPath("$.eligibility.status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.eligibility.conditionResults[0].type").value("AGE"))
                .andExpect(jsonPath("$.availability.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.availability.reason").value("WITHIN_APPLICATION_PERIOD"));
    }
}
