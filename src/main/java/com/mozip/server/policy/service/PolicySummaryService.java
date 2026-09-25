package com.mozip.server.policy.service;

import com.mozip.server.ai.service.PolicySummaryGenerationService;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicySummary;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.policy.repository.PolicySummaryRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class PolicySummaryService {

    private static final String GENERATION_VERSION = "v1";
    /**
     * AI에 보내는 원문 필드별 최대 길이. 정부24 원문이 수천 자인 정책은 요약 생성이 timeout(운영 nginx 15초 이내)을
     * 넘기기 쉬워 앞부분만 보낸다. 캐시 해시는 전체 원문 기준이라 원문이 바뀌면 그대로 다시 생성된다.
     */
    static final int MAX_SOURCE_LENGTH = 1500;

    private final PolicyRepository policyRepository;
    private final PolicySummaryRepository policySummaryRepository;
    private final PolicySummaryGenerationService policySummaryGenerationService;
    private final PolicySummaryPersistenceService policySummaryPersistenceService;

    public PolicySummaryService(PolicyRepository policyRepository,
                                 PolicySummaryRepository policySummaryRepository,
                                 PolicySummaryGenerationService policySummaryGenerationService,
                                 PolicySummaryPersistenceService policySummaryPersistenceService) {
        this.policyRepository = policyRepository;
        this.policySummaryRepository = policySummaryRepository;
        this.policySummaryGenerationService = policySummaryGenerationService;
        this.policySummaryPersistenceService = policySummaryPersistenceService;
    }

    public String getSummary(Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException(policyId));

        if (!hasAnySource(policy)) {
            return fallbackSummary(policy);
        }

        String currentHash = computeSourceHash(policy);
        Optional<PolicySummary> existing = policySummaryRepository.findByPolicyId(policyId);

        if (existing.isPresent() && existing.get().getSourceHash().equals(currentHash)) {
            return existing.get().getContent();
        }

        String aiContent = policySummaryGenerationService.generate(
                policy.getTitle(), truncate(policy.getDescription()), truncate(policy.getTargetDescription()),
                truncate(policy.getBenefitDescription()));

        if (aiContent == null) {
            return fallbackSummary(policy);
        }

        if (existing.isPresent()) {
            return policySummaryPersistenceService
                    .updateExisting(existing.get().getId(), aiContent, currentHash)
                    .getContent();
        }

        try {
            return policySummaryPersistenceService
                    .insertNew(policyId, aiContent, currentHash)
                    .getContent();
        } catch (DataIntegrityViolationException e) {
            Optional<PolicySummary> winner = policySummaryRepository.findByPolicyId(policyId);
            if (winner.isEmpty()) {
                throw e;
            }
            if (winner.get().getSourceHash().equals(currentHash)) {
                return winner.get().getContent();
            }
            return fallbackSummary(policy);
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_SOURCE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_SOURCE_LENGTH);
    }

    private boolean hasAnySource(Policy policy) {
        return isNonBlank(policy.getDescription())
                || isNonBlank(policy.getTargetDescription())
                || isNonBlank(policy.getBenefitDescription());
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String fallbackSummary(Policy policy) {
        String summary = policy.getSummary();
        if (summary == null || summary.isBlank()) {
            return null;
        }
        return summary;
    }

    private String computeSourceHash(Policy policy) {
        String canonical = canonicalize(GENERATION_VERSION)
                + canonicalize(policy.getTitle())
                + canonicalize(policy.getDescription())
                + canonicalize(policy.getTargetDescription())
                + canonicalize(policy.getBenefitDescription());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    private String canonicalize(String value) {
        String text = value == null ? "" : value;
        int byteLength = text.getBytes(StandardCharsets.UTF_8).length;
        return byteLength + ":" + text + ";";
    }
}
