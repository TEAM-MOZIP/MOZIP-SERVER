package com.mozip.server.recommendation.controller;

import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.recommendation.dto.PolicyRecommendationResponse;
import com.mozip.server.recommendation.service.PolicyRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Recommendation", description = "정책 추천/판정 API")
@RestController
public class PolicyRecommendationController {

    private final PolicyRecommendationService policyRecommendationService;

    public PolicyRecommendationController(PolicyRecommendationService policyRecommendationService) {
        this.policyRecommendationService = policyRecommendationService;
    }

    @Operation(summary = "추천 정책 목록 조회",
            description = "로그인한 사용자 기준으로 정책 목록에 대한 적격성과 신청 가능 여부를 함께 조회한다. "
                    + "정렬은 적격성 판정 결과(적격 → 보류 → 부적격) 기준으로 고정되며, 요청의 sort 파라미터는 반영되지 않는다. "
                    + "onlyEligible=true면 적격(ELIGIBLE) 판정을 받은 정책만 반환한다.")
    @GetMapping("/api/recommendations/policies")
    public PageResponse<PolicyRecommendationResponse> getRecommendations(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) PolicyStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean onlyEligible,
            @PageableDefault(size = 20) Pageable pageable) {
        PolicySearchRequest condition = new PolicySearchRequest(keyword, categoryId, regionId, status, null);
        return policyRecommendationService.getRecommendations(Long.valueOf(userId), condition, onlyEligible, pageable);
    }
}
