package com.mozip.server.policy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mozip.server.ai.exception.TermExplanationUnavailableException;
import com.mozip.server.auth.config.CustomAccessDeniedHandler;
import com.mozip.server.auth.config.CustomAuthenticationEntryPoint;
import com.mozip.server.auth.config.SecurityConfig;
import com.mozip.server.auth.jwt.JwtTokenProvider;
import com.mozip.server.policy.dto.TermExplanationRequest;
import com.mozip.server.policy.dto.TermExplanationResponse;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.service.TermExplanationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TermExplanationController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class TermExplanationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TermExplanationService termExplanationService;

    @Test
    void 인증_없이_요청하면_401이다() throws Exception {
        mockMvc.perform(post("/api/policies/1/terms/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증된_사용자는_용어_설명을_받는다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        when(termExplanationService.explain(eq(1L), any()))
                .thenReturn(new TermExplanationResponse("가구원 수에 따라 정해진 기준소득의 1.2배 이하를 의미합니다."));

        mockMvc.perform(post("/api/policies/1/terms/explain")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation").value("가구원 수에 따라 정해진 기준소득의 1.2배 이하를 의미합니다."));
    }

    @Test
    void 존재하지_않는_정책이면_404다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        when(termExplanationService.explain(eq(999L), any()))
                .thenThrow(new PolicyNotFoundException(999L));

        mockMvc.perform(post("/api/policies/999/terms/explain")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));
    }

    @Test
    void AI_실패시_503이다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        when(termExplanationService.explain(eq(1L), any()))
                .thenThrow(new TermExplanationUnavailableException());

        mockMvc.perform(post("/api/policies/1/terms/explain")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TERM_EXPLANATION_UNAVAILABLE"));
    }

    @Test
    void term이_빈문자열이면_400이다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");

        mockMvc.perform(post("/api/policies/1/terms/explain")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TermExplanationRequest("", "context"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void context가_빈문자열이면_400이다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");

        mockMvc.perform(post("/api/policies/1/terms/explain")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TermExplanationRequest("term", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private TermExplanationRequest request() {
        return new TermExplanationRequest("기준중위소득 120%", "본 사업은 기준중위소득 120% 이하의 청년을 대상으로 합니다.");
    }
}
