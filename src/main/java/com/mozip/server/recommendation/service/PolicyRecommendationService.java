package com.mozip.server.recommendation.service;

import com.mozip.server.ai.service.SemanticMatchService;
import com.mozip.server.bookmark.repository.BookmarkRepository;
import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.domain.PolicyPackage;
import com.mozip.server.policy.domain.PolicyPackageSection;
import com.mozip.server.policy.domain.PolicyPackageSelector;
import com.mozip.server.policy.dto.PolicyPackageDetailResponse;
import com.mozip.server.policy.dto.PolicyPackageSectionResponse;
import com.mozip.server.policy.dto.PolicyPackageSummaryResponse;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.entity.Category;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyCategory;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyRegion;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.evaluator.PolicyAvailabilityEvaluator;
import com.mozip.server.policy.exception.PolicyPackageNotFoundException;
import com.mozip.server.policy.repository.PolicyCategoryRepository;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRegionRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.policy.repository.PolicySpecifications;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import com.mozip.server.recommendation.domain.PolicyRecommendationCandidate;
import com.mozip.server.recommendation.dto.PolicyRecommendationResponse;
import com.mozip.server.recommendation.evaluator.PolicyEligibilityEvaluator;
import com.mozip.server.recommendation.evaluator.PolicyRecommendationComparator;
import com.mozip.server.region.entity.Region;
import com.mozip.server.user.entity.UserProfile;
import com.mozip.server.user.exception.UserProfileNotFoundException;
import com.mozip.server.user.repository.UserProfileRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PolicyRecommendationService {

    private static final PolicySearchRequest EMPTY_CONDITION = new PolicySearchRequest(null, null, null, null, null);
    private static final int PACKAGE_PREVIEW_SIZE = 6;

    private final UserProfileRepository userProfileRepository;
    private final PolicyRepository policyRepository;
    private final PolicyEligibilityRepository policyEligibilityRepository;
    private final PolicyRegionRepository policyRegionRepository;
    private final BookmarkRepository bookmarkRepository;
    private final PolicyCategoryRepository policyCategoryRepository;
    private final PolicyEligibilityEvaluator policyEligibilityEvaluator;
    private final PolicyAvailabilityEvaluator policyAvailabilityEvaluator;
    private final SemanticMatchService semanticMatchService;

    public PolicyRecommendationService(UserProfileRepository userProfileRepository, PolicyRepository policyRepository,
                                        PolicyEligibilityRepository policyEligibilityRepository,
                                        PolicyRegionRepository policyRegionRepository,
                                        BookmarkRepository bookmarkRepository,
                                        PolicyCategoryRepository policyCategoryRepository,
                                        PolicyEligibilityEvaluator policyEligibilityEvaluator,
                                        PolicyAvailabilityEvaluator policyAvailabilityEvaluator,
                                        SemanticMatchService semanticMatchService) {
        this.userProfileRepository = userProfileRepository;
        this.policyRepository = policyRepository;
        this.policyEligibilityRepository = policyEligibilityRepository;
        this.policyRegionRepository = policyRegionRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.policyCategoryRepository = policyCategoryRepository;
        this.policyEligibilityEvaluator = policyEligibilityEvaluator;
        this.policyAvailabilityEvaluator = policyAvailabilityEvaluator;
        this.semanticMatchService = semanticMatchService;
    }

    public PageResponse<PolicyRecommendationResponse> getRecommendations(Long userId, PolicySearchRequest condition,
                                                                          boolean onlyEligible, Pageable pageable) {
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));

        CandidateBuildResult buildResult = buildCandidateContext(userProfile, condition);

        List<PolicyRecommendationCandidate> filteredCandidates = onlyEligible
                ? buildResult.candidates().stream()
                        .filter(candidate -> candidate.eligibilityResult().overallStatus() == EligibilityStatus.ELIGIBLE)
                        .toList()
                : buildResult.candidates();

        Map<Long, Double> semanticScoreByPolicyId = getSemanticScores(userProfile, filteredCandidates, buildResult);

        List<PolicyRecommendationCandidate> sortedCandidates = filteredCandidates.stream()
                .map(candidate -> withSemanticScore(candidate, semanticScoreByPolicyId.get(candidate.policy().getId())))
                .sorted(PolicyRecommendationComparator.comparator())
                .toList();

        return toPageResponse(sortedCandidates, userId, pageable);
    }

    /**
     * 대상자별 패키지 카드용: 패키지마다 사용자 기준 정책 수를 센다(INELIGIBLE 제외).
     * 개수만 필요해 Semantic Match는 호출하지 않는다.
     */
    public List<PolicyPackageSummaryResponse> getPackages(Long userId) {
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));
        PackageContext context = loadPackageContext(userProfile);

        return Arrays.stream(PolicyPackage.values())
                .map(policyPackage -> new PolicyPackageSummaryResponse(policyPackage.getId(),
                        PolicyPackageSelector.countPolicies(groupByPackage(policyPackage, context.candidates(), context),
                                PolicyRecommendationCandidate::policy)))
                .toList();
    }

    /** 패키지 상세: 섹션별 전체 개수와 미리보기({@value #PACKAGE_PREVIEW_SIZE}개)를 담는다. */
    public PolicyPackageDetailResponse<PolicyRecommendationResponse> getPackage(Long userId, String packageId) {
        PolicyPackage policyPackage = PolicyPackage.fromId(packageId)
                .orElseThrow(() -> new PolicyPackageNotFoundException(packageId));
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));

        Map<PolicyPackageSection, List<PolicyRecommendationCandidate>> grouped =
                selectPersonalizedPackage(userProfile, policyPackage);
        List<PolicyPackageSectionResponse<PolicyRecommendationResponse>> sections = grouped.entrySet().stream()
                .map(entry -> new PolicyPackageSectionResponse<>(entry.getKey().key(), entry.getKey().name(),
                        entry.getValue().size(),
                        toRecommendationResponses(entry.getValue().stream().limit(PACKAGE_PREVIEW_SIZE).toList(),
                                userId)))
                .toList();
        return new PolicyPackageDetailResponse<>(policyPackage.getId(),
                PolicyPackageSelector.countPolicies(grouped, PolicyRecommendationCandidate::policy), sections);
    }

    /** 패키지 섹션 전체를 페이지 단위로 조회한다(더보기). */
    public PageResponse<PolicyRecommendationResponse> getPackageSectionPolicies(Long userId, String packageId,
                                                                              String sectionKey, Pageable pageable) {
        PolicyPackage policyPackage = PolicyPackage.fromId(packageId)
                .orElseThrow(() -> new PolicyPackageNotFoundException(packageId));
        PolicyPackageSection section = policyPackage.findSection(sectionKey)
                .orElseThrow(() -> new PolicyPackageNotFoundException(packageId, sectionKey));
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));

        return toPageResponse(selectPersonalizedPackage(userProfile, policyPackage).get(section), userId, pageable);
    }

    /**
     * INELIGIBLE을 뺀 후보 중 패키지 대상만 골라 Semantic Match 점수를 채우고,
     * 기존 개인화 추천 정렬(신청 가능 여부 → 적격성 → 적합도 점수 → 마감일)로 정렬해 섹션에 배정한다.
     */
    private Map<PolicyPackageSection, List<PolicyRecommendationCandidate>> selectPersonalizedPackage(
            UserProfile userProfile, PolicyPackage policyPackage) {
        PackageContext context = loadPackageContext(userProfile);
        List<PolicyRecommendationCandidate> members = PolicyPackageSelector.distinctCandidates(
                groupByPackage(policyPackage, context.candidates(), context), PolicyRecommendationCandidate::policy);

        Map<Long, Double> semanticScoreByPolicyId = getSemanticScores(userProfile, members, context.buildResult());
        List<PolicyRecommendationCandidate> sortedMembers = members.stream()
                .map(candidate -> withSemanticScore(candidate, semanticScoreByPolicyId.get(candidate.policy().getId())))
                .sorted(PolicyRecommendationComparator.comparator())
                .toList();
        return groupByPackage(policyPackage, sortedMembers, context);
    }

    private PackageContext loadPackageContext(UserProfile userProfile) {
        CandidateBuildResult buildResult = buildCandidateContext(userProfile, EMPTY_CONDITION);
        List<PolicyRecommendationCandidate> candidates = buildResult.candidates().stream()
                .filter(candidate -> candidate.eligibilityResult().overallStatus() != EligibilityStatus.INELIGIBLE)
                .toList();
        List<Long> policyIds = candidates.stream().map(candidate -> candidate.policy().getId()).toList();
        return new PackageContext(buildResult, candidates, groupCategoriesByPolicyId(policyIds));
    }

    private Map<PolicyPackageSection, List<PolicyRecommendationCandidate>> groupByPackage(
            PolicyPackage policyPackage, List<PolicyRecommendationCandidate> candidates, PackageContext context) {
        return PolicyPackageSelector.select(policyPackage, candidates, PolicyRecommendationCandidate::policy,
                PolicyRecommendationCandidate::availabilityResult, context.buildResult().eligibilityByPolicyId(),
                context.categoriesByPolicyId());
    }

    private List<PolicyRecommendationResponse> toRecommendationResponses(List<PolicyRecommendationCandidate> candidates,
                                                                         Long userId) {
        List<Long> policyIds = candidates.stream().map(candidate -> candidate.policy().getId()).toList();
        Set<Long> bookmarkedPolicyIds = policyIds.isEmpty()
                ? Set.of()
                : Set.copyOf(bookmarkRepository.findBookmarkedPolicyIds(userId, policyIds));
        Map<Long, List<Category>> categoriesByPolicyId = groupCategoriesByPolicyId(policyIds);
        Map<Long, List<Region>> regionsByPolicyId = groupRegionsByPolicyId(policyIds);
        // 목록 카드의 연령 칩 표시용 나이 범위
        Map<Long, PolicyEligibility> eligibilityByPolicyId = policyIds.isEmpty()
                ? Map.of()
                : policyEligibilityRepository.findByPolicyIdIn(policyIds).stream()
                        .collect(Collectors.toMap(eligibility -> eligibility.getPolicy().getId(), Function.identity()));
        return candidates.stream()
                .map(candidate -> PolicyRecommendationResponse.from(candidate.policy(), candidate.eligibilityResult(),
                        candidate.availabilityResult(), bookmarkedPolicyIds.contains(candidate.policy().getId()),
                        candidate.semanticScore(),
                        categoriesByPolicyId.getOrDefault(candidate.policy().getId(), List.of()),
                        regionsByPolicyId.getOrDefault(candidate.policy().getId(), List.of()),
                        eligibilityByPolicyId.get(candidate.policy().getId())))
                .toList();
    }

    /**
     * 정책 조회 + eligibility/availability 평가까지만 수행하고 semanticScore는 채우지 않은 채(null) 반환한다.
     * Semantic Match 요청 생성에 필요한 eligibility/region 조회 결과도 함께 반환해 재조회를 피한다.
     */
    private CandidateBuildResult buildCandidateContext(UserProfile userProfile, PolicySearchRequest condition) {
        Specification<Policy> spec = Specification.allOf(
                PolicySpecifications.keywordContains(condition.keyword()),
                PolicySpecifications.hasCategory(condition.categoryId()),
                PolicySpecifications.availableInRegion(condition.regionId()),
                PolicySpecifications.hasStatus(condition.status()),
                PolicySpecifications.hasAvailability(condition.availability(), policyAvailabilityEvaluator.today())
        );
        List<Policy> policies = policyRepository.findAll(spec, Sort.unsorted());

        List<Long> policyIds = policies.stream().map(Policy::getId).toList();
        List<Long> regionalPolicyIds = policies.stream()
                .filter(policy -> policy.getRegionScope() == RegionScope.REGIONAL)
                .map(Policy::getId)
                .toList();

        Map<Long, PolicyEligibility> eligibilityByPolicyId = policyIds.isEmpty()
                ? Map.of()
                : policyEligibilityRepository.findByPolicyIdIn(policyIds).stream()
                        .collect(Collectors.toMap(eligibility -> eligibility.getPolicy().getId(), Function.identity()));

        Map<Long, List<PolicyRegion>> policyRegionsByPolicyId = regionalPolicyIds.isEmpty()
                ? Map.of()
                : policyRegionRepository.findByPolicyIdIn(regionalPolicyIds).stream()
                        .collect(Collectors.groupingBy(policyRegion -> policyRegion.getPolicy().getId()));

        List<PolicyRecommendationCandidate> candidates = policies.stream()
                .map(policy -> {
                    PolicyEligibility eligibility = eligibilityByPolicyId.get(policy.getId());
                    List<Long> regionIds = policyRegionsByPolicyId.getOrDefault(policy.getId(), List.of()).stream()
                            .map(policyRegion -> policyRegion.getRegion().getId())
                            .toList();
                    PolicyEligibilityResult eligibilityResult =
                            policyEligibilityEvaluator.evaluate(userProfile, policy, regionIds, eligibility);
                    PolicyAvailabilityResult availabilityResult = policyAvailabilityEvaluator.evaluate(policy);
                    return new PolicyRecommendationCandidate(policy, eligibilityResult, availabilityResult, null);
                })
                .toList();

        return new CandidateBuildResult(candidates, eligibilityByPolicyId, policyRegionsByPolicyId);
    }

    private Map<Long, Double> getSemanticScores(UserProfile userProfile, List<PolicyRecommendationCandidate> candidates,
                                                 CandidateBuildResult buildResult) {
        if (candidates.isEmpty()) {
            return Map.of();
        }
        List<Policy> policies = candidates.stream().map(PolicyRecommendationCandidate::policy).toList();
        return semanticMatchService.matchScores(userProfile, policies,
                buildResult.eligibilityByPolicyId(), buildResult.policyRegionsByPolicyId());
    }

    private PolicyRecommendationCandidate withSemanticScore(PolicyRecommendationCandidate candidate, Double semanticScore) {
        return new PolicyRecommendationCandidate(candidate.policy(), candidate.eligibilityResult(),
                candidate.availabilityResult(), semanticScore);
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

    private PageResponse<PolicyRecommendationResponse> toPageResponse(List<PolicyRecommendationCandidate> candidates,
                                                                        Long userId, Pageable pageable) {
        int totalElements = candidates.size();
        int size = pageable.getPageSize();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        long offset = pageable.getOffset();

        List<PolicyRecommendationCandidate> pageContent = offset >= totalElements
                ? List.of()
                : candidates.subList((int) offset, (int) Math.min(offset + size, totalElements));

        List<PolicyRecommendationResponse> content = toRecommendationResponses(pageContent, userId);

        boolean first = pageable.getPageNumber() == 0;
        boolean last = pageable.getPageNumber() >= totalPages - 1;

        return new PageResponse<>(content, pageable.getPageNumber(), size, totalElements, totalPages, first, last);
    }

    private record PackageContext(
            CandidateBuildResult buildResult,
            List<PolicyRecommendationCandidate> candidates,
            Map<Long, List<Category>> categoriesByPolicyId
    ) {
    }

    private record CandidateBuildResult(
            List<PolicyRecommendationCandidate> candidates,
            Map<Long, PolicyEligibility> eligibilityByPolicyId,
            Map<Long, List<PolicyRegion>> policyRegionsByPolicyId
    ) {
    }
}
