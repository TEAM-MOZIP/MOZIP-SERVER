package com.mozip.server.recommendation.controller;

import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.domain.AvailabilityFilter;
import com.mozip.server.policy.dto.PolicyPackageDetailResponse;
import com.mozip.server.policy.dto.PolicyPackageSummaryResponse;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.recommendation.dto.PolicyRecommendationResponse;
import com.mozip.server.recommendation.service.PolicyRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
            @RequestParam(required = false) AvailabilityFilter availability,
            @RequestParam(required = false, defaultValue = "false") boolean onlyEligible,
            @PageableDefault(size = 20) Pageable pageable) {
        PolicySearchRequest condition =
                new PolicySearchRequest(keyword, categoryId, regionId, status, null, availability);
        return policyRecommendationService.getRecommendations(Long.valueOf(userId), condition, onlyEligible, pageable);
    }

    @Operation(summary = "개인화 정책 패키지 목록 조회",
            description = "로그인 및 프로필 등록 사용자 기준으로 대상자별 패키지(job-seeker, solo-youth, senior, teen)의 "
                    + "정책 수를 조회한다. INELIGIBLE 판정 정책은 제외한다.")
    @GetMapping("/api/recommendations/packages")
    public List<PolicyPackageSummaryResponse> getPackages(@AuthenticationPrincipal String userId) {
        return policyRecommendationService.getPackages(Long.valueOf(userId));
    }

    @Operation(summary = "개인화 정책 패키지 상세 조회",
            description = "패키지의 섹션별 전체 정책 수와 미리보기 정책(섹션당 최대 6개)을 사용자 기준으로 조회한다. "
                    + "INELIGIBLE과 마감된 정책은 제외하며, 개인화 추천 목록과 같은 기준(신청 가능 여부 → 적격성 → 적합도 점수)으로 정렬한다.")
    @GetMapping("/api/recommendations/packages/{packageId}")
    public PolicyPackageDetailResponse<PolicyRecommendationResponse> getPackage(
            @AuthenticationPrincipal String userId,
            @PathVariable String packageId) {
        return policyRecommendationService.getPackage(Long.valueOf(userId), packageId);
    }

    @Operation(summary = "개인화 정책 패키지 섹션 조회",
            description = "패키지 섹션의 전체 정책을 사용자 기준으로 페이지 단위로 조회한다(더보기). 정렬은 상세 조회와 같다.")
    @GetMapping("/api/recommendations/packages/{packageId}/sections/{sectionKey}")
    public PageResponse<PolicyRecommendationResponse> getPackageSectionPolicies(
            @AuthenticationPrincipal String userId,
            @PathVariable String packageId,
            @PathVariable String sectionKey,
            @PageableDefault(size = 12) Pageable pageable) {
        return policyRecommendationService.getPackageSectionPolicies(Long.valueOf(userId), packageId, sectionKey,
                pageable);
    }
}
