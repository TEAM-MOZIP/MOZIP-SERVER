package com.mozip.server.policy.controller;

import com.mozip.server.policy.dto.ApplicationGuideResponse;
import com.mozip.server.policy.service.ApplicationGuideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Policy", description = "정책 조회 API")
@RestController
@RequestMapping("/api/policies")
public class ApplicationGuideController {

    private final ApplicationGuideService applicationGuideService;

    public ApplicationGuideController(ApplicationGuideService applicationGuideService) {
        this.applicationGuideService = applicationGuideService;
    }

    @Operation(summary = "정책 신청 가이드 조회",
            description = "로그인한 사용자가 정책의 신청 조건/절차/서류/기간/URL/문의처/주의사항을 한 번에 조회한다.")
    @GetMapping("/{policyId}/application-guide")
    public ApplicationGuideResponse getApplicationGuide(@PathVariable Long policyId) {
        return applicationGuideService.getApplicationGuide(policyId);
    }
}
