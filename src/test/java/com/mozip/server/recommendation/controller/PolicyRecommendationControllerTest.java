package com.mozip.server.recommendation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mozip.server.auth.config.CustomAccessDeniedHandler;
import com.mozip.server.auth.config.CustomAuthenticationEntryPoint;
import com.mozip.server.auth.config.SecurityConfig;
import com.mozip.server.auth.jwt.JwtTokenProvider;
import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.dto.PolicyEvaluationResponse;
import com.mozip.server.recommendation.dto.PolicyRecommendationResponse;
import com.mozip.server.recommendation.service.PolicyRecommendationService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PolicyRecommendationController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class PolicyRecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private PolicyRecommendationService policyRecommendationService;

    @Test
    void 인증_없이_조회하면_401이다() throws Exception {
        mockMvc.perform(get("/api/recommendations/policies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증된_사용자는_추천_목록을_조회한다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        PolicyRecommendationResponse item = new PolicyRecommendationResponse(
                1L, "청년내일채움공제", "고용노동부",
                ApplicationType.PERIOD, LocalDate.now(), LocalDate.now().plusMonths(3),
                new PolicyEvaluationResponse.EligibilityResponse(
                        EligibilityStatus.ELIGIBLE, "모든 자동 판정 조건을 충족했습니다.", List.of()),
                new PolicyEvaluationResponse.AvailabilityResponse(
                        PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.WITHIN_APPLICATION_PERIOD, true),
                true
        );
        PageResponse<PolicyRecommendationResponse> page = new PageResponse<>(List.of(item), 0, 20, 1, 1, true, true);
        when(policyRecommendationService.getRecommendations(eq(1L), any(PolicySearchRequest.class), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/recommendations/policies")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].policyId").value(1))
                .andExpect(jsonPath("$.content[0].title").value("청년내일채움공제"))
                .andExpect(jsonPath("$.content[0].organizationName").value("고용노동부"))
                .andExpect(jsonPath("$.content[0].eligibility.status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.content[0].availability.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[0].availability.closingSoon").value(true))
                .andExpect(jsonPath("$.content[0].bookmarked").value(true));
    }

    @Test
    void 북마크된_정책과_북마크되지_않은_정책이_함께_있으면_각각_다르게_직렬화된다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        PolicyRecommendationResponse bookmarkedItem = new PolicyRecommendationResponse(
                1L, "청년내일채움공제", "고용노동부",
                ApplicationType.PERIOD, LocalDate.now(), LocalDate.now().plusMonths(3),
                new PolicyEvaluationResponse.EligibilityResponse(
                        EligibilityStatus.ELIGIBLE, "모든 자동 판정 조건을 충족했습니다.", List.of()),
                new PolicyEvaluationResponse.AvailabilityResponse(
                        PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.WITHIN_APPLICATION_PERIOD, false),
                true
        );
        PolicyRecommendationResponse notBookmarkedItem = new PolicyRecommendationResponse(
                2L, "국민취업지원제도", "고용노동부",
                ApplicationType.PERIOD, LocalDate.now(), LocalDate.now().plusMonths(3),
                new PolicyEvaluationResponse.EligibilityResponse(
                        EligibilityStatus.ELIGIBLE, "모든 자동 판정 조건을 충족했습니다.", List.of()),
                new PolicyEvaluationResponse.AvailabilityResponse(
                        PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.WITHIN_APPLICATION_PERIOD, false),
                false
        );
        PageResponse<PolicyRecommendationResponse> page = new PageResponse<>(
                List.of(bookmarkedItem, notBookmarkedItem), 0, 20, 2, 1, true, true);
        when(policyRecommendationService.getRecommendations(eq(1L), any(PolicySearchRequest.class), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/recommendations/policies")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].policyId").value(1))
                .andExpect(jsonPath("$.content[0].bookmarked").value(true))
                .andExpect(jsonPath("$.content[1].policyId").value(2))
                .andExpect(jsonPath("$.content[1].bookmarked").value(false));
    }

    @Test
    void 검색_파라미터와_Pageable이_Service에_전달된다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        PageResponse<PolicyRecommendationResponse> page = new PageResponse<>(List.of(), 0, 5, 0, 0, true, true);
        when(policyRecommendationService.getRecommendations(eq(1L), any(PolicySearchRequest.class), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/recommendations/policies")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("keyword", "청년")
                        .param("categoryId", "2")
                        .param("regionId", "3")
                        .param("status", "OPEN")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<PolicySearchRequest> conditionCaptor = ArgumentCaptor.forClass(PolicySearchRequest.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(policyRecommendationService).getRecommendations(eq(1L), conditionCaptor.capture(), pageableCaptor.capture());

        PolicySearchRequest capturedCondition = conditionCaptor.getValue();
        assertThat(capturedCondition.keyword()).isEqualTo("청년");
        assertThat(capturedCondition.categoryId()).isEqualTo(2L);
        assertThat(capturedCondition.regionId()).isEqualTo(3L);
        assertThat(capturedCondition.status()).isEqualTo(PolicyStatus.OPEN);
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }
}
