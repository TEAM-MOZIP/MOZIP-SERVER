package com.mozip.server.recommendation.service;

import com.mozip.server.bookmark.repository.BookmarkRepository;
import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.evaluator.PolicyAvailabilityEvaluator;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRegionRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.policy.repository.PolicySpecifications;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import com.mozip.server.recommendation.domain.PolicyRecommendationCandidate;
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

    private final UserProfileRepository userProfileRepository;
    private final PolicyRepository policyRepository;
    private final PolicyEligibilityRepository policyEligibilityRepository;
    private final PolicyRegionRepository policyRegionRepository;
    private final BookmarkRepository bookmarkRepository;
    private final PolicyEligibilityEvaluator policyEligibilityEvaluator;
    private final PolicyAvailabilityEvaluator policyAvailabilityEvaluator;

    public PolicyRecommendationService(UserProfileRepository userProfileRepository, PolicyRepository policyRepository,
                                        PolicyEligibilityRepository policyEligibilityRepository,
                                        PolicyRegionRepository policyRegionRepository,
                                        BookmarkRepository bookmarkRepository,
                                        PolicyEligibilityEvaluator policyEligibilityEvaluator,
                                        PolicyAvailabilityEvaluator policyAvailabilityEvaluator) {
        this.userProfileRepository = userProfileRepository;
        this.policyRepository = policyRepository;
        this.policyEligibilityRepository = policyEligibilityRepository;
        this.policyRegionRepository = policyRegionRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.policyEligibilityEvaluator = policyEligibilityEvaluator;
        this.policyAvailabilityEvaluator = policyAvailabilityEvaluator;
    }

    public PageResponse<PolicyRecommendationResponse> getRecommendations(Long userId, PolicySearchRequest condition,
                                                                          Pageable pageable) {
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));

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

        Map<Long, List<Long>> regionIdsByPolicyId = regionalPolicyIds.isEmpty()
                ? Map.of()
                : policyRegionRepository.findByPolicyIdIn(regionalPolicyIds).stream()
                        .collect(Collectors.groupingBy(policyRegion -> policyRegion.getPolicy().getId(),
                                Collectors.mapping(policyRegion -> policyRegion.getRegion().getId(), Collectors.toList())));

        List<PolicyRecommendationCandidate> candidates = policies.stream()
                .map(policy -> {
                    PolicyEligibility eligibility = eligibilityByPolicyId.get(policy.getId());
                    List<Long> regionIds = regionIdsByPolicyId.getOrDefault(policy.getId(), List.of());
                    PolicyEligibilityResult eligibilityResult =
                            policyEligibilityEvaluator.evaluate(userProfile, policy, regionIds, eligibility);
                    PolicyAvailabilityResult availabilityResult = policyAvailabilityEvaluator.evaluate(policy);
                    return new PolicyRecommendationCandidate(policy, eligibilityResult, availabilityResult);
                })
                .sorted(PolicyRecommendationComparator.comparator())
                .toList();

        return toPageResponse(candidates, userId, pageable);
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
                        candidate.availabilityResult(), bookmarkedPolicyIds.contains(candidate.policy().getId())))
                .toList();

        boolean first = pageable.getPageNumber() == 0;
        boolean last = pageable.getPageNumber() >= totalPages - 1;

        return new PageResponse<>(content, pageable.getPageNumber(), size, totalElements, totalPages, first, last);
    }
}
