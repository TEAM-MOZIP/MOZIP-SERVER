package com.mozip.server.recommendation.service;

import com.mozip.server.ai.service.SemanticMatchService;
import com.mozip.server.bookmark.repository.BookmarkRepository;
import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.domain.PolicyPackageGrouper;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.entity.Category;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyCategory;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyRegion;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.evaluator.PolicyAvailabilityEvaluator;
import com.mozip.server.policy.repository.PolicyCategoryRepository;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRegionRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.policy.repository.PolicySpecifications;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import com.mozip.server.recommendation.domain.PolicyRecommendationCandidate;
import com.mozip.server.recommendation.dto.PolicyPackageResponse;
import com.mozip.server.recommendation.dto.PolicyRecommendationResponse;
import com.mozip.server.recommendation.evaluator.PolicyEligibilityEvaluator;
import com.mozip.server.recommendation.evaluator.PolicyRecommendationComparator;
import com.mozip.server.user.entity.UserProfile;
import com.mozip.server.user.exception.UserProfileNotFoundException;
import com.mozip.server.user.repository.UserProfileRepository;
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

    public List<PolicyPackageResponse> getPackages(Long userId) {
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));

        CandidateBuildResult buildResult = buildCandidateContext(userProfile, EMPTY_CONDITION);
        List<PolicyRecommendationCandidate> candidates = buildResult.candidates().stream()
                .filter(candidate -> candidate.eligibilityResult().overallStatus() != EligibilityStatus.INELIGIBLE)
                .sorted(PolicyRecommendationComparator.comparator())
                .toList();

        List<Long> policyIds = candidates.stream().map(candidate -> candidate.policy().getId()).toList();
        Map<Long, List<Category>> categoriesByPolicyId = groupCategoriesByPolicyId(policyIds);

        Map<Category, List<PolicyRecommendationCandidate>> grouped =
                PolicyPackageGrouper.group(candidates, PolicyRecommendationCandidate::policy, categoriesByPolicyId);

        List<Long> exposedPolicyIds = grouped.values().stream()
                .flatMap(List::stream)
                .map(candidate -> candidate.policy().getId())
                .distinct()
                .toList();
        Set<Long> bookmarkedPolicyIds = exposedPolicyIds.isEmpty()
                ? Set.of()
                : Set.copyOf(bookmarkRepository.findBookmarkedPolicyIds(userId, exposedPolicyIds));

        return grouped.entrySet().stream()
                .map(entry -> PolicyPackageResponse.from(entry.getKey(),
                        entry.getValue().stream()
                                .map(candidate -> PolicyRecommendationResponse.from(candidate.policy(), candidate.eligibilityResult(),
                                        candidate.availabilityResult(), bookmarkedPolicyIds.contains(candidate.policy().getId()), null))
                                .toList()))
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
                PolicySpecifications.hasStatus(condition.status())
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

    private PageResponse<PolicyRecommendationResponse> toPageResponse(List<PolicyRecommendationCandidate> candidates,
                                                                        Long userId, Pageable pageable) {
        int totalElements = candidates.size();
        int size = pageable.getPageSize();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        long offset = pageable.getOffset();

        List<PolicyRecommendationCandidate> pageContent = offset >= totalElements
                ? List.of()
                : candidates.subList((int) offset, (int) Math.min(offset + size, totalElements));

        List<Long> pagePolicyIds = pageContent.stream().map(candidate -> candidate.policy().getId()).toList();
        Set<Long> bookmarkedPolicyIds = pagePolicyIds.isEmpty()
                ? Set.of()
                : Set.copyOf(bookmarkRepository.findBookmarkedPolicyIds(userId, pagePolicyIds));

        List<PolicyRecommendationResponse> content = pageContent.stream()
                .map(candidate -> PolicyRecommendationResponse.from(candidate.policy(), candidate.eligibilityResult(),
                        candidate.availabilityResult(), bookmarkedPolicyIds.contains(candidate.policy().getId()),
                        candidate.semanticScore()))
                .toList();

        boolean first = pageable.getPageNumber() == 0;
        boolean last = pageable.getPageNumber() >= totalPages - 1;

        return new PageResponse<>(content, pageable.getPageNumber(), size, totalElements, totalPages, first, last);
    }

    private record CandidateBuildResult(
            List<PolicyRecommendationCandidate> candidates,
            Map<Long, PolicyEligibility> eligibilityByPolicyId,
            Map<Long, List<PolicyRegion>> policyRegionsByPolicyId
    ) {
    }
}
