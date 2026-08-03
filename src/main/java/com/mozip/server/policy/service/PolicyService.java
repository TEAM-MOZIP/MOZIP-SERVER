package com.mozip.server.policy.service;

import com.mozip.server.bookmark.repository.BookmarkRepository;
import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.domain.PolicyAvailabilityCandidate;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.domain.PolicyPackageGrouper;
import com.mozip.server.policy.dto.PolicyDetailResponse;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.dto.PolicySummaryResponse;
import com.mozip.server.policy.dto.PublicPolicyPackageResponse;
import com.mozip.server.policy.entity.Category;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyCategory;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.evaluator.PolicyAvailabilityComparator;
import com.mozip.server.policy.evaluator.PolicyAvailabilityEvaluator;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyCategoryRepository;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.policy.repository.PolicySpecifications;
import java.util.List;
import java.util.Map;
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

    private final PolicyRepository policyRepository;
    private final PolicyEligibilityRepository policyEligibilityRepository;
    private final PolicyAvailabilityEvaluator policyAvailabilityEvaluator;
    private final BookmarkRepository bookmarkRepository;
    private final PolicyCategoryRepository policyCategoryRepository;

    public PolicyService(PolicyRepository policyRepository, PolicyEligibilityRepository policyEligibilityRepository,
                          PolicyAvailabilityEvaluator policyAvailabilityEvaluator, BookmarkRepository bookmarkRepository,
                          PolicyCategoryRepository policyCategoryRepository) {
        this.policyRepository = policyRepository;
        this.policyEligibilityRepository = policyEligibilityRepository;
        this.policyAvailabilityEvaluator = policyAvailabilityEvaluator;
        this.bookmarkRepository = bookmarkRepository;
        this.policyCategoryRepository = policyCategoryRepository;
    }

    public PageResponse<PolicySummaryResponse> searchPolicies(PolicySearchRequest condition, Pageable pageable) {
        Specification<Policy> spec = Specification.allOf(
                PolicySpecifications.keywordContains(condition.keyword()),
                PolicySpecifications.hasCategory(condition.categoryId()),
                PolicySpecifications.availableInRegion(condition.regionId()),
                PolicySpecifications.hasStatus(condition.status()),
                PolicySpecifications.hasAgeGroup(condition.ageGroup())
        );

        Page<Policy> policies = policyRepository.findAll(spec, pageable);
        Page<PolicySummaryResponse> summaries = policies.map(
                policy -> PolicySummaryResponse.from(policy, policyAvailabilityEvaluator.evaluate(policy)));
        return PageResponse.from(summaries);
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
        List<PolicyAvailabilityCandidate> candidates = getSortedAvailabilityCandidates(condition);
        return toPageResponse(candidates, pageable);
    }

    public List<PublicPolicyPackageResponse> getPackages() {
        List<PolicyAvailabilityCandidate> candidates = getSortedAvailabilityCandidates(EMPTY_CONDITION);

        List<Long> policyIds = candidates.stream().map(candidate -> candidate.policy().getId()).toList();
        Map<Long, List<Category>> categoriesByPolicyId = groupCategoriesByPolicyId(policyIds);

        return PolicyPackageGrouper.group(candidates, PolicyAvailabilityCandidate::policy, categoriesByPolicyId)
                .entrySet().stream()
                .map(entry -> PublicPolicyPackageResponse.from(entry.getKey(),
                        entry.getValue().stream()
                                .map(candidate -> PolicySummaryResponse.from(candidate.policy(), candidate.availabilityResult()))
                                .toList()))
                .toList();
    }

    private List<PolicyAvailabilityCandidate> getSortedAvailabilityCandidates(PolicySearchRequest condition) {
        Specification<Policy> spec = Specification.allOf(
                PolicySpecifications.keywordContains(condition.keyword()),
                PolicySpecifications.hasCategory(condition.categoryId()),
                PolicySpecifications.availableInRegion(condition.regionId()),
                PolicySpecifications.hasStatus(condition.status())
        );
        List<Policy> policies = policyRepository.findAll(spec, Sort.unsorted());

        return policies.stream()
                .map(policy -> new PolicyAvailabilityCandidate(policy, policyAvailabilityEvaluator.evaluate(policy)))
                .sorted(PolicyAvailabilityComparator.comparator())
                .toList();
    }

    private Map<Long, List<Category>> groupCategoriesByPolicyId(List<Long> policyIds) {
        if (policyIds.isEmpty()) {
            return Map.of();
        }
        return policyCategoryRepository.findByPolicyIdIn(policyIds).stream()
                .collect(Collectors.groupingBy(policyCategory -> policyCategory.getPolicy().getId(),
                        Collectors.mapping(PolicyCategory::getCategory, Collectors.toList())));
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

        List<PolicySummaryResponse> content = pageContent.stream()
                .map(candidate -> PolicySummaryResponse.from(candidate.policy(), candidate.availabilityResult()))
                .toList();

        boolean first = pageable.getPageNumber() == 0;
        boolean last = pageable.getPageNumber() >= totalPages - 1;

        return new PageResponse<>(content, pageable.getPageNumber(), size, totalElements, totalPages, first, last);
    }
}