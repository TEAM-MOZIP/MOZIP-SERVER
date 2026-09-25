package com.mozip.server.policy.service;

import com.mozip.server.bookmark.repository.BookmarkRepository;
import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.domain.PolicyAvailabilityCandidate;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.domain.PolicyPackage;
import com.mozip.server.policy.domain.PolicyPackageSection;
import com.mozip.server.policy.domain.PolicyPackageSelector;
import com.mozip.server.policy.dto.PolicyDetailResponse;
import com.mozip.server.policy.dto.PolicyPackageDetailResponse;
import com.mozip.server.policy.dto.PolicyPackageSectionResponse;
import com.mozip.server.policy.dto.PolicyPackageSummaryResponse;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.dto.PolicySummaryResponse;
import com.mozip.server.policy.entity.Category;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyCategory;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyRegion;
import com.mozip.server.policy.evaluator.PolicyAvailabilityComparator;
import com.mozip.server.policy.evaluator.PolicyAvailabilityEvaluator;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.exception.PolicyPackageNotFoundException;
import com.mozip.server.policy.repository.PolicyCategoryRepository;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRegionRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.policy.repository.PolicySpecifications;
import com.mozip.server.region.entity.Region;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PolicyService {

    private static final PolicySearchRequest EMPTY_CONDITION = new PolicySearchRequest(null, null, null, null, null);
    private static final int PACKAGE_PREVIEW_SIZE = 6;

    private final PolicyRepository policyRepository;
    private final PolicyEligibilityRepository policyEligibilityRepository;
    private final PolicyAvailabilityEvaluator policyAvailabilityEvaluator;
    private final BookmarkRepository bookmarkRepository;
    private final PolicyCategoryRepository policyCategoryRepository;
    private final PolicyRegionRepository policyRegionRepository;

    public PolicyService(PolicyRepository policyRepository, PolicyEligibilityRepository policyEligibilityRepository,
                          PolicyAvailabilityEvaluator policyAvailabilityEvaluator, BookmarkRepository bookmarkRepository,
                          PolicyCategoryRepository policyCategoryRepository,
                          PolicyRegionRepository policyRegionRepository) {
        this.policyRepository = policyRepository;
        this.policyEligibilityRepository = policyEligibilityRepository;
        this.policyAvailabilityEvaluator = policyAvailabilityEvaluator;
        this.bookmarkRepository = bookmarkRepository;
        this.policyCategoryRepository = policyCategoryRepository;
        this.policyRegionRepository = policyRegionRepository;
    }

    public PageResponse<PolicySummaryResponse> searchPolicies(PolicySearchRequest condition, Pageable pageable) {
        return searchPolicies(condition, pageable, null);
    }

    /** userId가 있으면(로그인) 각 정책의 북마크 여부를 함께 채운다. */
    public PageResponse<PolicySummaryResponse> searchPolicies(PolicySearchRequest condition, Pageable pageable,
                                                              Long userId) {
        Specification<Policy> spec = Specification.allOf(
                PolicySpecifications.keywordContains(condition.keyword()),
                PolicySpecifications.hasCategory(condition.categoryId()),
                PolicySpecifications.availableInRegion(condition.regionId()),
                PolicySpecifications.hasStatus(condition.status()),
                PolicySpecifications.hasAgeGroup(condition.ageGroup()),
                PolicySpecifications.hasAvailability(condition.availability(), policyAvailabilityEvaluator.today())
        );

        Page<Policy> policies = policyRepository.findAll(spec, pageable);
        List<Long> policyIds = policies.getContent().stream().map(Policy::getId).toList();
        Map<Long, List<Category>> categoriesByPolicyId = groupCategoriesByPolicyId(policyIds);
        Map<Long, List<Region>> regionsByPolicyId = groupRegionsByPolicyId(policyIds);
        Map<Long, PolicyEligibility> eligibilityByPolicyId = findEligibilitiesByPolicyId(policyIds);
        Page<PolicySummaryResponse> summaries = policies.map(
                policy -> PolicySummaryResponse.from(policy, policyAvailabilityEvaluator.evaluate(policy),
                        categoriesByPolicyId.getOrDefault(policy.getId(), List.of()),
                        regionsByPolicyId.getOrDefault(policy.getId(), List.of()),
                        eligibilityByPolicyId.get(policy.getId())));
        return withBookmarks(PageResponse.from(summaries), userId);
    }

    public PolicyDetailResponse getPolicyDetail(Long policyId, Long userId) {
        Policy policy = policyRepository.findWithOrganizationById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException(policyId));
        PolicyEligibility eligibility = policyEligibilityRepository.findByPolicyId(policyId)
                .orElse(null);
        PolicyAvailabilityResult availabilityResult = policyAvailabilityEvaluator.evaluate(policy);
        boolean bookmarked = userId != null && bookmarkRepository.existsByUserIdAndPolicyId(userId, policyId);
        return PolicyDetailResponse.from(policy, eligibility, availabilityResult, bookmarked);
    }

    public PageResponse<PolicySummaryResponse> getRecommendedPolicies(PolicySearchRequest condition, Pageable pageable) {
        return getRecommendedPolicies(condition, pageable, null);
    }

    /** userId가 있으면(로그인) 각 정책의 북마크 여부를 함께 채운다. */
    public PageResponse<PolicySummaryResponse> getRecommendedPolicies(PolicySearchRequest condition, Pageable pageable,
                                                                      Long userId) {
        List<PolicyAvailabilityCandidate> candidates = getSortedAvailabilityCandidates(condition);
        return withBookmarks(toPageResponse(candidates, pageable), userId);
    }

    /**
     * 로그인 사용자가 맞춤 추천 대신 공개 목록(다른 연령 보기 등)을 볼 때도 북마크 상태가 보이도록,
     * 현재 페이지 정책들의 북마크 여부를 한 번에 조회해 채운다.
     */
    private PageResponse<PolicySummaryResponse> withBookmarks(PageResponse<PolicySummaryResponse> page, Long userId) {
        if (userId == null || page.content().isEmpty()) {
            return page;
        }
        List<Long> policyIds = page.content().stream().map(PolicySummaryResponse::id).toList();
        Set<Long> bookmarkedIds = new HashSet<>(bookmarkRepository.findBookmarkedPolicyIds(userId, policyIds));
        List<PolicySummaryResponse> content = page.content().stream()
                .map(summary -> summary.withBookmarked(bookmarkedIds.contains(summary.id())))
                .toList();
        return new PageResponse<>(content, page.page(), page.size(), page.totalElements(), page.totalPages(),
                page.first(), page.last());
    }

    /** 대상자별 패키지 카드용: 패키지마다 정책 수를 센다. */
    public List<PolicyPackageSummaryResponse> getPackages() {
        PublicPackageContext context = loadPublicPackageContext();
        return Arrays.stream(PolicyPackage.values())
                .map(policyPackage -> new PolicyPackageSummaryResponse(policyPackage.getId(),
                        PolicyPackageSelector.countPolicies(selectPublicPackage(policyPackage, context),
                                PolicyAvailabilityCandidate::policy)))
                .toList();
    }

    /** 패키지 상세: 섹션별 전체 개수와 미리보기({@value #PACKAGE_PREVIEW_SIZE}개)를 담는다. */
    public PolicyPackageDetailResponse<PolicySummaryResponse> getPackage(String packageId) {
        PolicyPackage policyPackage = PolicyPackage.fromId(packageId)
                .orElseThrow(() -> new PolicyPackageNotFoundException(packageId));
        Map<PolicyPackageSection, List<PolicyAvailabilityCandidate>> grouped =
                selectPublicPackage(policyPackage, loadPublicPackageContext());

        List<PolicyPackageSectionResponse<PolicySummaryResponse>> sections = grouped.entrySet().stream()
                .map(entry -> new PolicyPackageSectionResponse<>(entry.getKey().key(), entry.getKey().name(),
                        entry.getValue().size(),
                        toSummaries(entry.getValue().stream().limit(PACKAGE_PREVIEW_SIZE).toList())))
                .toList();
        return new PolicyPackageDetailResponse<>(policyPackage.getId(),
                PolicyPackageSelector.countPolicies(grouped, PolicyAvailabilityCandidate::policy), sections);
    }

    /** 패키지 섹션 전체를 페이지 단위로 조회한다(더보기). */
    public PageResponse<PolicySummaryResponse> getPackageSectionPolicies(String packageId, String sectionKey,
                                                                        Pageable pageable) {
        PolicyPackage policyPackage = PolicyPackage.fromId(packageId)
                .orElseThrow(() -> new PolicyPackageNotFoundException(packageId));
        PolicyPackageSection section = policyPackage.findSection(sectionKey)
                .orElseThrow(() -> new PolicyPackageNotFoundException(packageId, sectionKey));
        List<PolicyAvailabilityCandidate> candidates =
                selectPublicPackage(policyPackage, loadPublicPackageContext()).get(section);
        return toPageResponse(candidates, pageable);
    }

    private PublicPackageContext loadPublicPackageContext() {
        List<PolicyAvailabilityCandidate> candidates = getSortedAvailabilityCandidates(EMPTY_CONDITION);
        List<Long> policyIds = candidates.stream().map(candidate -> candidate.policy().getId()).toList();
        Map<Long, PolicyEligibility> eligibilityByPolicyId = findEligibilitiesByPolicyId(policyIds);
        return new PublicPackageContext(candidates, eligibilityByPolicyId, groupCategoriesByPolicyId(policyIds));
    }

    private Map<PolicyPackageSection, List<PolicyAvailabilityCandidate>> selectPublicPackage(
            PolicyPackage policyPackage, PublicPackageContext context) {
        List<PolicyAvailabilityCandidate> sorted = context.candidates().stream()
                .sorted(PolicyPackageSelector.publicOrder(policyPackage, PolicyAvailabilityCandidate::policy,
                        PolicyAvailabilityCandidate::availabilityResult, context.eligibilityByPolicyId()))
                .toList();
        return PolicyPackageSelector.select(policyPackage, sorted, PolicyAvailabilityCandidate::policy,
                PolicyAvailabilityCandidate::availabilityResult, context.eligibilityByPolicyId(),
                context.categoriesByPolicyId());
    }

    private List<PolicySummaryResponse> toSummaries(List<PolicyAvailabilityCandidate> candidates) {
        List<Long> policyIds = candidates.stream().map(candidate -> candidate.policy().getId()).toList();
        Map<Long, List<Category>> categoriesByPolicyId = groupCategoriesByPolicyId(policyIds);
        Map<Long, List<Region>> regionsByPolicyId = groupRegionsByPolicyId(policyIds);
        Map<Long, PolicyEligibility> eligibilityByPolicyId = findEligibilitiesByPolicyId(policyIds);
        return candidates.stream()
                .map(candidate -> PolicySummaryResponse.from(candidate.policy(), candidate.availabilityResult(),
                        categoriesByPolicyId.getOrDefault(candidate.policy().getId(), List.of()),
                        regionsByPolicyId.getOrDefault(candidate.policy().getId(), List.of()),
                        eligibilityByPolicyId.get(candidate.policy().getId())))
                .toList();
    }

    private record PublicPackageContext(
            List<PolicyAvailabilityCandidate> candidates,
            Map<Long, PolicyEligibility> eligibilityByPolicyId,
            Map<Long, List<Category>> categoriesByPolicyId
    ) {
    }

    private List<PolicyAvailabilityCandidate> getSortedAvailabilityCandidates(PolicySearchRequest condition) {
        Specification<Policy> spec = Specification.allOf(
                PolicySpecifications.keywordContains(condition.keyword()),
                PolicySpecifications.hasCategory(condition.categoryId()),
                PolicySpecifications.availableInRegion(condition.regionId()),
                PolicySpecifications.hasStatus(condition.status()),
                PolicySpecifications.hasAgeGroup(condition.ageGroup()),
                PolicySpecifications.hasAvailability(condition.availability(), policyAvailabilityEvaluator.today())
        );
        List<Policy> policies = policyRepository.findAll(spec, Sort.unsorted());

        return policies.stream()
                .map(policy -> new PolicyAvailabilityCandidate(policy, policyAvailabilityEvaluator.evaluate(policy)))
                .sorted(PolicyAvailabilityComparator.comparator())
                .toList();
    }

    /** 목록 카드의 연령 칩 표시용: 정책별 자격 조건(나이 범위)을 한 번에 조회한다. */
    private Map<Long, PolicyEligibility> findEligibilitiesByPolicyId(List<Long> policyIds) {
        if (policyIds.isEmpty()) {
            return Map.of();
        }
        return policyEligibilityRepository.findByPolicyIdIn(policyIds).stream()
                .collect(Collectors.toMap(eligibility -> eligibility.getPolicy().getId(), Function.identity()));
    }

    private Map<Long, List<Category>> groupCategoriesByPolicyId(List<Long> policyIds) {
        if (policyIds.isEmpty()) {
            return Map.of();
        }
        return policyCategoryRepository.findByPolicyIdIn(policyIds).stream()
                .collect(Collectors.groupingBy(policyCategory -> policyCategory.getPolicy().getId(),
                        Collectors.mapping(PolicyCategory::getCategory, Collectors.toList())));
    }

    private Map<Long, List<Region>> groupRegionsByPolicyId(List<Long> policyIds) {
        if (policyIds.isEmpty()) {
            return Map.of();
        }
        return policyRegionRepository.findByPolicyIdIn(policyIds).stream()
                .collect(Collectors.groupingBy(policyRegion -> policyRegion.getPolicy().getId(),
                        Collectors.mapping(PolicyRegion::getRegion, Collectors.toList())));
    }

    private PageResponse<PolicySummaryResponse> toPageResponse(List<PolicyAvailabilityCandidate> candidates,
                                                                 Pageable pageable) {
        int totalElements = candidates.size();
        int size = pageable.getPageSize();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        long offset = pageable.getOffset();

        List<PolicyAvailabilityCandidate> pageContent = offset >= totalElements
                ? List.of()
                : candidates.subList((int) offset, (int) Math.min(offset + size, totalElements));

        List<Long> pagePolicyIds = pageContent.stream().map(candidate -> candidate.policy().getId()).toList();
        Map<Long, List<Category>> categoriesByPolicyId = groupCategoriesByPolicyId(pagePolicyIds);
        Map<Long, List<Region>> regionsByPolicyId = groupRegionsByPolicyId(pagePolicyIds);
        Map<Long, PolicyEligibility> eligibilityByPolicyId = findEligibilitiesByPolicyId(pagePolicyIds);
        List<PolicySummaryResponse> content = pageContent.stream()
                .map(candidate -> PolicySummaryResponse.from(candidate.policy(), candidate.availabilityResult(),
                        categoriesByPolicyId.getOrDefault(candidate.policy().getId(), List.of()),
                        regionsByPolicyId.getOrDefault(candidate.policy().getId(), List.of()),
                        eligibilityByPolicyId.get(candidate.policy().getId())))
                .toList();

        boolean first = pageable.getPageNumber() == 0;
        boolean last = pageable.getPageNumber() >= totalPages - 1;

        return new PageResponse<>(content, pageable.getPageNumber(), size, totalElements, totalPages, first, last);
    }
}