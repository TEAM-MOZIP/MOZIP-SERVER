package com.mozip.server.recommendation.service;

import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRegionRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import com.mozip.server.recommendation.evaluator.PolicyEligibilityEvaluator;
import com.mozip.server.user.entity.UserProfile;
import com.mozip.server.user.exception.UserProfileNotFoundException;
import com.mozip.server.user.repository.UserProfileRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PolicyEligibilityService {

    private final UserProfileRepository userProfileRepository;
    private final PolicyRepository policyRepository;
    private final PolicyEligibilityRepository policyEligibilityRepository;
    private final PolicyRegionRepository policyRegionRepository;
    private final PolicyEligibilityEvaluator policyEligibilityEvaluator;

    public PolicyEligibilityService(UserProfileRepository userProfileRepository, PolicyRepository policyRepository,
                                     PolicyEligibilityRepository policyEligibilityRepository,
                                     PolicyRegionRepository policyRegionRepository,
                                     PolicyEligibilityEvaluator policyEligibilityEvaluator) {
        this.userProfileRepository = userProfileRepository;
        this.policyRepository = policyRepository;
        this.policyEligibilityRepository = policyEligibilityRepository;
        this.policyRegionRepository = policyRegionRepository;
        this.policyEligibilityEvaluator = policyEligibilityEvaluator;
    }

    public PolicyEligibilityResult evaluate(Long userId, Long policyId) {
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException(policyId));
        PolicyEligibility eligibility = policyEligibilityRepository.findByPolicyId(policyId)
                .orElse(null);
        List<Long> policyRegionIds = (eligibility != null && policy.getRegionScope() == RegionScope.REGIONAL)
                ? policyRegionRepository.findRegionIdsByPolicyId(policyId)
                : List.of();

        return policyEligibilityEvaluator.evaluate(userProfile, policy, policyRegionIds, eligibility);
    }
}
