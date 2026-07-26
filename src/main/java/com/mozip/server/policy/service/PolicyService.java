package com.mozip.server.policy.service;

import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.dto.PolicyDetailResponse;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.dto.PolicySummaryResponse;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.evaluator.PolicyAvailabilityEvaluator;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.policy.repository.PolicySpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final PolicyEligibilityRepository policyEligibilityRepository;
    private final PolicyAvailabilityEvaluator policyAvailabilityEvaluator;

    public PolicyService(PolicyRepository policyRepository, PolicyEligibilityRepository policyEligibilityRepository,
                          PolicyAvailabilityEvaluator policyAvailabilityEvaluator) {
        this.policyRepository = policyRepository;
        this.policyEligibilityRepository = policyEligibilityRepository;
        this.policyAvailabilityEvaluator = policyAvailabilityEvaluator;
    }

    public PageResponse<PolicySummaryResponse> searchPolicies(PolicySearchRequest condition, Pageable pageable) {
        Specification<Policy> spec = Specification.allOf(
                PolicySpecifications.keywordContains(condition.keyword()),
                PolicySpecifications.hasCategory(condition.categoryId()),
                PolicySpecifications.availableInRegion(condition.regionId()),
                PolicySpecifications.hasStatus(condition.status())
        );

        Page<Policy> policies = policyRepository.findAll(spec, pageable);
        Page<PolicySummaryResponse> summaries = policies.map(
                policy -> PolicySummaryResponse.from(policy, policyAvailabilityEvaluator.evaluate(policy)));
        return PageResponse.from(summaries);
    }

    public PolicyDetailResponse getPolicyDetail(Long policyId) {
        Policy policy = policyRepository.findWithOrganizationById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException(policyId));
        PolicyEligibility eligibility = policyEligibilityRepository.findByPolicyId(policyId)
                .orElse(null);
        PolicyAvailabilityResult availabilityResult = policyAvailabilityEvaluator.evaluate(policy);
        return PolicyDetailResponse.from(policy, eligibility, availabilityResult);
    }
}