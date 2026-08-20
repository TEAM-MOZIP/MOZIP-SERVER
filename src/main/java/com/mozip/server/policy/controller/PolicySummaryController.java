package com.mozip.server.policy.controller;

import com.mozip.server.policy.dto.PolicySummaryContentResponse;
import com.mozip.server.policy.service.PolicySummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Policy", description = "정책 조회 API")
@RestController
@RequestMapping("/api/policies")
public class PolicySummaryController {

    private final PolicySummaryService policySummaryService;

    public PolicySummaryController(PolicySummaryService policySummaryService) {
        this.policySummaryService = policySummaryService;
    }

    @Operation(summary = "정책 요약 조회",
            description = "로그인한 사용자가 정책 원문을 종합한 AI 요약을 조회한다. 최초 요청 시 생성 후 재사용된다.")
    @GetMapping("/{policyId}/summary")
    public PolicySummaryContentResponse getSummary(@PathVariable Long policyId) {
        return PolicySummaryContentResponse.of(policyId, policySummaryService.getSummary(policyId));
    }
}
