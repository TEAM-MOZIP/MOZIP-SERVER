package com.mozip.server.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mozip.server.ai.client.SemanticMatchClient;
import com.mozip.server.ai.dto.SemanticMatchRequest;
import com.mozip.server.ai.dto.SemanticMatchResponse;
import com.mozip.server.ai.dto.SemanticMatchResult;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.user.entity.UserProfile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

class SemanticMatchServiceTest {

    private final SemanticMatchClient semanticMatchClient = mock(SemanticMatchClient.class);
    private final SemanticMatchService semanticMatchService = new SemanticMatchService(semanticMatchClient);

    @Test
    void 정상_응답이면_policyId_기준으로_score_맵을_구성한다() {
        Policy policy1 = policy(101L, RegionScope.NATIONAL);
        Policy policy2 = policy(102L, RegionScope.NATIONAL);
        when(semanticMatchClient.match(any())).thenReturn(new SemanticMatchResponse(List.of(
                new SemanticMatchResult(101L, 0.75, List.of(), List.of()),
                new SemanticMatchResult(102L, 0.2, List.of(), List.of())
        )));

        Map<Long, Double> scores = semanticMatchService.matchScores(
                UserProfile.builder().build(), List.of(policy1, policy2), Map.of(), Map.of());

        assertThat(scores).containsEntry(101L, 0.75).containsEntry(102L, 0.2);
    }

    @Test
    void 응답의_semanticScore가_null이면_그대로_null로_유지한다() {
        Policy policy = policy(101L, RegionScope.NATIONAL);
        when(semanticMatchClient.match(any())).thenReturn(new SemanticMatchResponse(List.of(
                new SemanticMatchResult(101L, null, List.of(), List.of())
        )));

        Map<Long, Double> scores = semanticMatchService.matchScores(
                UserProfile.builder().build(), List.of(policy), Map.of(), Map.of());

        assertThat(scores).containsKey(101L);
        assertThat(scores.get(101L)).isNull();
    }

    @Test
    void 응답에_없는_policyId는_맵에_아예_포함되지_않는다() {
        Policy policy1 = policy(101L, RegionScope.NATIONAL);
        Policy policy2 = policy(102L, RegionScope.NATIONAL);
        when(semanticMatchClient.match(any())).thenReturn(new SemanticMatchResponse(List.of(
                new SemanticMatchResult(101L, 0.5, List.of(), List.of())
        )));

        Map<Long, Double> scores = semanticMatchService.matchScores(
                UserProfile.builder().build(), List.of(policy1, policy2), Map.of(), Map.of());

        assertThat(scores).containsOnlyKeys(101L);
    }

    @Test
    void AI_호출이_RestClientException을_던지면_빈_맵을_반환한다() {
        Policy policy = policy(101L, RegionScope.NATIONAL);
        when(semanticMatchClient.match(any())).thenThrow(new ResourceAccessException("연결 실패"));

        Map<Long, Double> scores = semanticMatchService.matchScores(
                UserProfile.builder().build(), List.of(policy), Map.of(), Map.of());

        assertThat(scores).isEmpty();
    }

    @Test
    void Client가_null을_반환해도_예외_없이_빈_맵을_반환한다() {
        Policy policy = policy(101L, RegionScope.NATIONAL);
        when(semanticMatchClient.match(any())).thenReturn(null);

        Map<Long, Double> scores = semanticMatchService.matchScores(
                UserProfile.builder().build(), List.of(policy), Map.of(), Map.of());

        assertThat(scores).isEmpty();
    }

    @Test
    void 응답의_results가_null이어도_예외_없이_빈_맵을_반환한다() {
        Policy policy = policy(101L, RegionScope.NATIONAL);
        when(semanticMatchClient.match(any())).thenReturn(new SemanticMatchResponse(null));

        Map<Long, Double> scores = semanticMatchService.matchScores(
                UserProfile.builder().build(), List.of(policy), Map.of(), Map.of());

        assertThat(scores).isEmpty();
    }

    @Test
    void 정책_목록이_비어있으면_Client를_호출하지_않는다() {
        Map<Long, Double> scores = semanticMatchService.matchScores(
                UserProfile.builder().build(), List.of(), Map.of(), Map.of());

        assertThat(scores).isEmpty();
        verify(semanticMatchClient, never()).match(any());
    }

    @Test
    void PolicyEligibility가_없는_정책도_요청에서_제외되지_않는다() {
        Policy withEligibility = policy(101L, RegionScope.NATIONAL);
        Policy withoutEligibility = policy(102L, RegionScope.NATIONAL);
        PolicyEligibility eligibility = PolicyEligibility.builder().policy(withEligibility).minimumAge(19).build();
        when(semanticMatchClient.match(any())).thenReturn(new SemanticMatchResponse(List.of()));

        semanticMatchService.matchScores(UserProfile.builder().build(), List.of(withEligibility, withoutEligibility),
                Map.of(101L, eligibility), Map.of());

        org.mockito.ArgumentCaptor<SemanticMatchRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(SemanticMatchRequest.class);
        verify(semanticMatchClient).match(requestCaptor.capture());
        assertThat(requestCaptor.getValue().policies()).extracting(p -> p.policyId())
                .containsExactlyInAnyOrder(101L, 102L);
        assertThat(requestCaptor.getValue().policies()).filteredOn(p -> p.policyId().equals(102L))
                .allMatch(p -> p.minimumAge() == null);
    }

    private Policy policy(Long id, RegionScope regionScope) {
        Organization organization = Organization.builder().name("테스트기관").build();
        Policy policy = Policy.builder()
                .organization(organization)
                .title("테스트정책")
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(regionScope)
                .status(PolicyStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }
}
