package com.mozip.server.ai.service;

import com.mozip.server.ai.client.SemanticMatchClient;
import com.mozip.server.ai.dto.SemanticMatchPolicyRequest;
import com.mozip.server.ai.dto.SemanticMatchRequest;
import com.mozip.server.ai.dto.SemanticMatchResponse;
import com.mozip.server.ai.dto.SemanticMatchResult;
import com.mozip.server.ai.dto.SemanticMatchUserRequest;
import com.mozip.server.ai.mapper.SemanticMatchRequestMapper;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyRegion;
import com.mozip.server.user.entity.UserProfile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class SemanticMatchService {

    private static final Logger log = LoggerFactory.getLogger(SemanticMatchService.class);

    private final SemanticMatchClient semanticMatchClient;

    public SemanticMatchService(SemanticMatchClient semanticMatchClient) {
        this.semanticMatchClient = semanticMatchClient;
    }

    public Map<Long, Double> matchScores(UserProfile userProfile, List<Policy> policies,
                                          Map<Long, PolicyEligibility> eligibilityByPolicyId,
                                          Map<Long, List<PolicyRegion>> policyRegionsByPolicyId) {
        if (policies.isEmpty()) {
            return Map.of();
        }

        SemanticMatchUserRequest userRequest = SemanticMatchRequestMapper.toUserRequest(userProfile);
        List<SemanticMatchPolicyRequest> policyRequests = policies.stream()
                .map(policy -> SemanticMatchRequestMapper.toPolicyRequest(
                        policy,
                        Optional.ofNullable(eligibilityByPolicyId.get(policy.getId())),
                        policyRegionsByPolicyId.getOrDefault(policy.getId(), List.of())))
                .toList();

        SemanticMatchResponse response;
        try {
            response = semanticMatchClient.match(new SemanticMatchRequest(userRequest, policyRequests));
        } catch (RestClientException e) {
            log.warn("Semantic Match 호출에 실패해 semanticScore 없이 추천을 반환합니다. exceptionType={}",
                    e.getClass().getSimpleName());
            return Map.of();
        }

        if (response == null || response.results() == null) {
            log.warn("Semantic Match 응답에 결과가 없어 semanticScore 없이 추천을 반환합니다.");
            return Map.of();
        }

        Map<Long, Double> scoresByPolicyId = new HashMap<>();
        for (SemanticMatchResult result : response.results()) {
            scoresByPolicyId.put(result.policyId(), result.semanticScore());
        }
        return scoresByPolicyId;
    }
}
