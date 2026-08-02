package com.mozip.server.policy.controller;

import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.domain.AgeGroup;
import com.mozip.server.policy.dto.PolicyDetailResponse;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.dto.PolicySummaryResponse;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.repository.PolicySortValidator;
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

    @Operation(summary = "정책 목록 조회",
            description = "키워드, 카테고리, 지역, 상태, 연령 구간 조건으로 정책을 검색하고 페이지 단위로 조회한다. "
                    + "sort는 createdAt, applicationEndDate만 허용하며 그 외 필드를 요청하면 400을 반환한다. "
                    + "정렬 뒤에는 결정성을 위한 id DESC가 자동으로 추가된다.")
    @GetMapping
    public PageResponse<PolicySummaryResponse> searchPolicies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) PolicyStatus status,
            @RequestParam(required = false) AgeGroup ageGroup,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Pageable validatedPageable = PolicySortValidator.validate(pageable);
        PolicySearchRequest condition = new PolicySearchRequest(keyword, categoryId, regionId, status, ageGroup);
        return policyService.searchPolicies(condition, validatedPageable);
    }

    @Operation(summary = "공개 추천 정책 목록 조회",
            description = "비로그인 사용자를 위한 공개 추천 목록을 조회한다. 개인화 적격성 판정 없이, "
                    + "신청 가능 여부(신청 가능 → 확인 필요 → 신청 불가)와 신청 마감일 기준으로 고정 정렬된다.")
    @GetMapping("/recommended")
    public PageResponse<PolicySummaryResponse> getRecommendedPolicies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) PolicyStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        PolicySearchRequest condition = new PolicySearchRequest(keyword, categoryId, regionId, status, null);
        return policyService.getRecommendedPolicies(condition, pageable);
    }

    @Operation(summary = "정책 상세 조회", description = "정책 ID로 상세 정보를 조회한다.")
    @GetMapping("/{id}")
    public PolicyDetailResponse getPolicyDetail(@PathVariable Long id) {
        return policyService.getPolicyDetail(id);
    }
}