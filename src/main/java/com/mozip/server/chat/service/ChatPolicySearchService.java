package com.mozip.server.chat.service;

import com.mozip.server.chat.domain.ChatCondition;
import com.mozip.server.chat.dto.ChatPolicyMatchResult;
import com.mozip.server.chat.evaluator.ChatConditionEvaluator;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyRegion;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRegionRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import com.mozip.server.region.entity.Region;
import com.mozip.server.region.repository.RegionRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ChatCondition으로 전체 정책을 조건 평가하는 orchestration이다. UserProfile 조회, semantic score,
 * bookmark 등 추천 전용 데이터는 사용하지 않는다. 순위 선정/상위 N개 제한은 D-3 orchestration의 몫이며
 * 이 서비스는 평가 결과 전체를 반환한다.
 */
@Service
@Transactional(readOnly = true)
public class ChatPolicySearchService {

    private final PolicyRepository policyRepository;
    private final PolicyEligibilityRepository policyEligibilityRepository;
    private final PolicyRegionRepository policyRegionRepository;
    private final RegionRepository regionRepository;
    private final ChatConditionEvaluator chatConditionEvaluator;

    public ChatPolicySearchService(PolicyRepository policyRepository,
                                    PolicyEligibilityRepository policyEligibilityRepository,
                                    PolicyRegionRepository policyRegionRepository,
                                    RegionRepository regionRepository,
                                    ChatConditionEvaluator chatConditionEvaluator) {
        this.policyRepository = policyRepository;
        this.policyEligibilityRepository = policyEligibilityRepository;
        this.policyRegionRepository = policyRegionRepository;
        this.regionRepository = regionRepository;
        this.chatConditionEvaluator = chatConditionEvaluator;
    }

    public List<ChatPolicyMatchResult> search(ChatCondition condition) {
        List<Policy> policies = policyRepository.findAll();
        if (policies.isEmpty()) {
            return List.of();
        }

        List<Long> policyIds = policies.stream().map(Policy::getId).toList();
        List<Long> regionalPolicyIds = policies.stream()
                .filter(policy -> policy.getRegionScope() == RegionScope.REGIONAL)
                .map(Policy::getId)
                .toList();

        Map<Long, PolicyEligibility> eligibilityByPolicyId = policyEligibilityRepository.findByPolicyIdIn(policyIds).stream()
                .collect(Collectors.toMap(eligibility -> eligibility.getPolicy().getId(), Function.identity()));

        Map<Long, List<PolicyRegion>> policyRegionsByPolicyId = regionalPolicyIds.isEmpty()
                ? Map.of()
                : policyRegionRepository.findByPolicyIdIn(regionalPolicyIds).stream()
                        .collect(Collectors.groupingBy(policyRegion -> policyRegion.getPolicy().getId()));

        Region userRegion = resolveUserRegion(condition.regionId());

        return policies.stream()
                .map(policy -> {
                    PolicyEligibility eligibility = eligibilityByPolicyId.get(policy.getId());
                    List<Long> regionIds = policyRegionsByPolicyId.getOrDefault(policy.getId(), List.of()).stream()
                            .map(policyRegion -> policyRegion.getRegion().getId())
                            .toList();
                    PolicyEligibilityResult eligibilityResult =
                            chatConditionEvaluator.evaluate(condition, policy, regionIds, eligibility, userRegion);
                    return new ChatPolicyMatchResult(policy, eligibilityResult);
                })
                .toList();
    }

    private Region resolveUserRegion(Long regionId) {
        return regionId == null ? null : regionRepository.findById(regionId).orElse(null);
    }
}
