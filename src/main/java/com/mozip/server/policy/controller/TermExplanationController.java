package com.mozip.server.policy.controller;

import com.mozip.server.policy.dto.TermExplanationRequest;
import com.mozip.server.policy.dto.TermExplanationResponse;
import com.mozip.server.policy.service.TermExplanationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Policy", description = "정책 조회 API")
@RestController
@RequestMapping("/api/policies")
public class TermExplanationController {

    private final TermExplanationService termExplanationService;

    public TermExplanationController(TermExplanationService termExplanationService) {
        this.termExplanationService = termExplanationService;
    }

    @Operation(summary = "정책 용어 설명", description = "로그인한 사용자가 정책 문맥(term/context) 안에서 특정 용어의 의미를 AI로 설명받는다.")
    @PostMapping("/{policyId}/terms/explain")
    public TermExplanationResponse explainTerm(@PathVariable Long policyId,
                                                @RequestBody @Valid TermExplanationRequest request) {
        return termExplanationService.explain(policyId, request);
    }
}
