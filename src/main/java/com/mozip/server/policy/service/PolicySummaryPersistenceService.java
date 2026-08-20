package com.mozip.server.policy.service;

import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicySummary;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.policy.repository.PolicySummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicySummaryPersistenceService {

    private final PolicyRepository policyRepository;
    private final PolicySummaryRepository policySummaryRepository;

    public PolicySummaryPersistenceService(PolicyRepository policyRepository,
                                            PolicySummaryRepository policySummaryRepository) {
        this.policyRepository = policyRepository;
        this.policySummaryRepository = policySummaryRepository;
    }

    @Transactional
    public PolicySummary insertNew(Long policyId, String content, String sourceHash) {
        Policy policyRef = policyRepository.getReferenceById(policyId);
        PolicySummary created = PolicySummary.builder()
                .policy(policyRef)
                .content(content)
                .sourceHash(sourceHash)
                .build();
        return policySummaryRepository.saveAndFlush(created);
    }

    @Transactional
    public PolicySummary updateExisting(Long existingId, String content, String sourceHash) {
        PolicySummary summary = policySummaryRepository.findById(existingId)
                .orElseThrow(() -> new IllegalStateException("PolicySummary가 존재하지 않습니다. id=" + existingId));
        summary.update(content, sourceHash);
        return summary;
    }
}
