package com.mozip.server.policy.controller;

import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.dto.PolicyDetailResponse;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.dto.PolicySummaryResponse;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Policy", description = "정책 조회 API")
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @Operation(summary = "정책 목록 조회", description = "키워드, 카테고리, 지역, 상태 조건으로 정책을 검색하고 페이지 단위로 조회한다.")
    @GetMapping
    public PageResponse<PolicySummaryResponse> searchPolicies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) PolicyStatus status,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        PolicySearchRequest condition = new PolicySearchRequest(keyword, categoryId, regionId, status);
        return policyService.searchPolicies(condition, pageable);
    }

    @Operation(summary = "정책 상세 조회", description = "정책 ID로 상세 정보를 조회한다.")
    @GetMapping("/{id}")
    public PolicyDetailResponse getPolicyDetail(@PathVariable Long id) {
        return policyService.getPolicyDetail(id);
    }
}