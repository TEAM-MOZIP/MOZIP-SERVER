package com.mozip.server.policy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.dto.PolicyDetailResponse;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.dto.PolicySummaryResponse;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.service.PolicyService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PolicyController.class)
class PolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyService policyService;

    @Test
    void 정책_목록_조회에_성공한다() throws Exception {
        PolicySummaryResponse summary = new PolicySummaryResponse(
                1L, "청년 월세 지원", "월세 지원 사업", "서울특별시",
                ApplicationType.PERIOD, LocalDate.now(), LocalDate.now().plusMonths(3),
                RegionScope.REGIONAL, PolicyStatus.OPEN
        );
        PageResponse<PolicySummaryResponse> page =
                new PageResponse<>(List.of(summary), 0, 20, 1, 1, true, true);
        when(policyService.searchPolicies(any(PolicySearchRequest.class), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("청년 월세 지원"));
    }

    @Test
    void 정책_상세_조회에_성공한다() throws Exception {
        PolicyDetailResponse detail = new PolicyDetailResponse(
                1L, "청년 월세 지원", "월세 지원 사업", "상세 설명", "지원 대상", "지원 내용", "온라인 신청",
                ApplicationType.PERIOD, LocalDate.now(), LocalDate.now().plusMonths(3),
                RegionScope.REGIONAL, PolicyStatus.OPEN, "https://example.com", "서울특별시", null
        );
        when(policyService.getPolicyDetail(1L)).thenReturn(detail);

        mockMvc.perform(get("/api/policies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("청년 월세 지원"));
    }

    @Test
    void 존재하지_않는_정책을_조회하면_404를_반환한다() throws Exception {
        when(policyService.getPolicyDetail(999L)).thenThrow(new PolicyNotFoundException(999L));

        mockMvc.perform(get("/api/policies/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));
    }

    @Test
    void 잘못된_상태값을_전달하면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/policies").param("status", "INVALID_VALUE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}