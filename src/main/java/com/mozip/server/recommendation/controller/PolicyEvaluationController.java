package com.mozip.server.recommendation.controller;

import com.mozip.server.recommendation.dto.PolicyEvaluationResponse;
import com.mozip.server.recommendation.service.PolicyEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Recommendation", description = "정책 추천/판정 API")
@RestController
public class PolicyEvaluationController {

    private final PolicyEvaluationService policyEvaluationService;

    public PolicyEvaluationController(PolicyEvaluationService policyEvaluationService) {
        this.policyEvaluationService = policyEvaluationService;
    }

    @Operation(summary = "정책 맞춤 판정 조회", description = "로그인한 사용자 기준으로 정책의 적격성과 신청 가능 여부를 함께 조회한다.")
    @GetMapping("/api/recommendations/policies/{policyId}/evaluation")
    public PolicyEvaluationResponse getEvaluation(@AuthenticationPrincipal String userId,
                                                   @PathVariable Long policyId) {
        return policyEvaluationService.evaluate(Long.valueOf(userId), policyId);
    }
}
