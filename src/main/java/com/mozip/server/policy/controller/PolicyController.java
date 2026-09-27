package com.mozip.server.policy.controller;

import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.domain.AgeGroup;
import com.mozip.server.policy.domain.AvailabilityFilter;
import com.mozip.server.policy.dto.PolicyDetailResponse;
import com.mozip.server.policy.dto.PolicyPackageDetailResponse;
import com.mozip.server.policy.dto.PolicyPackageSummaryResponse;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.dto.PolicySummaryResponse;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.repository.PolicySortValidator;
import com.mozip.server.policy.service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
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
                    + "정렬 뒤에는 결정성을 위한 id DESC가 자동으로 추가된다. "
                    + "로그인한 사용자면 정책별 북마크 여부(bookmarked)를 함께 반환하고, 비로그인이면 null이다.")
    @GetMapping
    public PageResponse<PolicySummaryResponse> searchPolicies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) PolicyStatus status,
            @RequestParam(required = false) AgeGroup ageGroup,
            @Parameter(description = "신청 상태 필터(OPEN=접수 중, CLOSING_SOON=마감 임박, UPCOMING=예정). 오늘 날짜 기준으로 계산한다.")
            @RequestParam(required = false) AvailabilityFilter availability,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        Pageable validatedPageable = PolicySortValidator.validate(pageable);
        PolicySearchRequest condition =
                new PolicySearchRequest(keyword, categoryId, regionId, status, ageGroup, availability);
        return policyService.searchPolicies(condition, validatedPageable, currentUserId(authentication));
    }

    @Operation(summary = "공개 추천 정책 목록 조회",
            description = "비로그인 사용자를 위한 공개 추천 목록을 조회한다. 개인화 적격성 판정 없이, "
                    + "신청 가능 여부(신청 가능 → 확인 필요 → 신청 불가)와 신청 마감일 기준으로 고정 정렬된다. "
                    + "연령 구간(ageGroup)으로 거를 수 있고, 로그인한 사용자면 북마크 여부(bookmarked)를 함께 반환한다.")
    @GetMapping("/recommended")
    public PageResponse<PolicySummaryResponse> getRecommendedPolicies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) PolicyStatus status,
            @RequestParam(required = false) AgeGroup ageGroup,
            @Parameter(description = "신청 상태 필터(OPEN=접수 중, CLOSING_SOON=마감 임박, UPCOMING=예정). 오늘 날짜 기준으로 계산한다.")
            @RequestParam(required = false) AvailabilityFilter availability,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        PolicySearchRequest condition =
                new PolicySearchRequest(keyword, categoryId, regionId, status, ageGroup, availability);
        return policyService.getRecommendedPolicies(condition, pageable, currentUserId(authentication));
    }

    @Operation(summary = "정책 상세 조회",
            description = "정책 ID로 상세 정보를 조회한다. 로그인한 사용자의 경우 북마크 여부를 함께 반환한다.")
    @GetMapping("/{id}")
    public PolicyDetailResponse getPolicyDetail(@PathVariable Long id, Authentication authentication) {
        return policyService.getPolicyDetail(id, currentUserId(authentication));
    }

    @Operation(summary = "공개 정책 패키지 목록 조회",
            description = "비로그인 사용자를 위한 대상자별 패키지(job-seeker, solo-youth, senior, teen)의 정책 수를 조회한다. "
                    + "패키지 제목·문구 등 표시 정보는 클라이언트가 packageId로 관리한다.")
    @GetMapping("/packages")
    public List<PolicyPackageSummaryResponse> getPackages() {
        return policyService.getPackages();
    }

    @Operation(summary = "공개 정책 패키지 상세 조회",
            description = "패키지의 섹션별 전체 정책 수와 미리보기 정책(섹션당 최대 6개)을 조회한다. "
                    + "마감된 정책은 제외하며, 접수 중 → 신청기간 확인 필요 → 예정 순으로 정렬한다.")
    @GetMapping("/packages/{packageId}")
    public PolicyPackageDetailResponse<PolicySummaryResponse> getPackage(@PathVariable String packageId) {
        return policyService.getPackage(packageId);
    }

    @Operation(summary = "공개 정책 패키지 섹션 조회",
            description = "패키지 섹션의 전체 정책을 페이지 단위로 조회한다(더보기). 정렬은 상세 조회와 같다.")
    @GetMapping("/packages/{packageId}/sections/{sectionKey}")
    public PageResponse<PolicySummaryResponse> getPackageSectionPolicies(
            @PathVariable String packageId,
            @PathVariable String sectionKey,
            @PageableDefault(size = 12) Pageable pageable) {
        return policyService.getPackageSectionPolicies(packageId, sectionKey, pageable);
    }

    private static Long currentUserId(Authentication authentication) {
        return authentication != null ? Long.valueOf(authentication.getName()) : null;
    }
}
