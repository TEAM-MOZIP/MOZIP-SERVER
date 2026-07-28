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
import com.mozip.server.recommendation.dto.PolicyRecommendationResponse;
import com.mozip.server.recommendation.evaluator.PolicyEligibilityEvaluator;
import com.mozip.server.user.entity.UserProfile;
import com.mozip.server.user.exception.UserProfileNotFoundException;
import com.mozip.server.user.repository.UserProfileRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        Page<Policy> policies = policyRepository.findAll(spec, pageable);

        List<Long> policyIds = policies.getContent().stream().map(Policy::getId).toList();
        List<Long> regionalPolicyIds = policies.getContent().stream()
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

        Set<Long> bookmarkedPolicyIds = policyIds.isEmpty()
                ? Set.of()
                : Set.copyOf(bookmarkRepository.findBookmarkedPolicyIds(userId, policyIds));

        Page<PolicyRecommendationResponse> responses = policies.map(policy -> {
            PolicyEligibility eligibility = eligibilityByPolicyId.get(policy.getId());
            List<Long> regionIds = regionIdsByPolicyId.getOrDefault(policy.getId(), List.of());
            boolean bookmarked = bookmarkedPolicyIds.contains(policy.getId());

            PolicyEligibilityResult eligibilityResult =
                    policyEligibilityEvaluator.evaluate(userProfile, policy, regionIds, eligibility);
            PolicyAvailabilityResult availabilityResult = policyAvailabilityEvaluator.evaluate(policy);

            return PolicyRecommendationResponse.from(policy, eligibilityResult, availabilityResult, bookmarked);
        });

        return PageResponse.from(responses);
    }
}
