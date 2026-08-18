package com.mozip.server.policy.service;

import com.mozip.server.ai.service.PolicyTermExplanationService;
import com.mozip.server.policy.dto.TermExplanationRequest;
import com.mozip.server.policy.dto.TermExplanationResponse;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TermExplanationService {

    private final PolicyRepository policyRepository;
    private final PolicyTermExplanationService policyTermExplanationService;

    public TermExplanationService(PolicyRepository policyRepository,
                                   PolicyTermExplanationService policyTermExplanationService) {
        this.policyRepository = policyRepository;
        this.policyTermExplanationService = policyTermExplanationService;
    }

    public TermExplanationResponse explain(Long policyId, TermExplanationRequest request) {
        if (!policyRepository.existsById(policyId)) {
            throw new PolicyNotFoundException(policyId);
        }
        return policyTermExplanationService.explain(request);
    }
}
