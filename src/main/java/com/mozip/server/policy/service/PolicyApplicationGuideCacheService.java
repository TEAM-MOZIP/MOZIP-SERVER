package com.mozip.server.policy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mozip.server.ai.dto.ApplicationGuideResponse;
import com.mozip.server.policy.entity.PolicyApplicationGuide;
import com.mozip.server.policy.repository.PolicyApplicationGuideRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 신청 가이드를 정책별로 저장해 두고, 가이드 원문(신청 절차·준비 서류)이 그대로면 AI를 다시 호출하지 않고 재사용한다.
 * 가이드 조회는 읽기 전용 트랜잭션 안에서 일어나므로, 저장은 별도 트랜잭션(REQUIRES_NEW)으로 한다.
 */
@Service
public class PolicyApplicationGuideCacheService {

    private static final Logger log = LoggerFactory.getLogger(PolicyApplicationGuideCacheService.class);

    /** 가이드 프롬프트·응답 형식이 바뀌면 올려서 기존 캐시를 무효화한다. */
    private static final String GENERATION_VERSION = "v1";

    private final PolicyRepository policyRepository;
    private final PolicyApplicationGuideRepository policyApplicationGuideRepository;
    private final ObjectMapper objectMapper;

    public PolicyApplicationGuideCacheService(PolicyRepository policyRepository,
                                              PolicyApplicationGuideRepository policyApplicationGuideRepository,
                                              ObjectMapper objectMapper) {
        this.policyRepository = policyRepository;
        this.policyApplicationGuideRepository = policyApplicationGuideRepository;
        this.objectMapper = objectMapper;
    }

    /** 원문이 같을 때 저장된 가이드. 없거나 원문이 바뀌었거나 읽을 수 없으면 empty. */
    @Transactional(readOnly = true)
    public Optional<ApplicationGuideResponse> find(Long policyId, String sourceHash) {
        return policyApplicationGuideRepository.findByPolicyId(policyId)
                .filter(cached -> cached.getSourceHash().equals(sourceHash))
                .flatMap(cached -> deserialize(policyId, cached.getContent()));
    }

    /** AI가 생성한 가이드를 저장(없으면 추가, 있으면 갱신)한다. 동시 저장으로 인한 중복 예외는 호출하는 쪽에서 무시한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Long policyId, ApplicationGuideResponse guide, String sourceHash) {
        String content = serialize(guide);
        Optional<PolicyApplicationGuide> existing = policyApplicationGuideRepository.findByPolicyId(policyId);
        if (existing.isPresent()) {
            existing.get().update(content, sourceHash);
            return;
        }
        policyApplicationGuideRepository.saveAndFlush(PolicyApplicationGuide.builder()
                .policy(policyRepository.getReferenceById(policyId))
                .content(content)
                .sourceHash(sourceHash)
                .build());
    }

    public static String sourceHash(String applicationInstructionsSource, String requiredDocumentsSource) {
        String canonical = canonicalize(GENERATION_VERSION)
                + canonicalize(applicationInstructionsSource)
                + canonicalize(requiredDocumentsSource);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    private String serialize(ApplicationGuideResponse guide) {
        try {
            return objectMapper.writeValueAsString(guide);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("신청 가이드를 저장할 수 없습니다.", e);
        }
    }

    private Optional<ApplicationGuideResponse> deserialize(Long policyId, String content) {
        try {
            return Optional.of(objectMapper.readValue(content, ApplicationGuideResponse.class));
        } catch (JsonProcessingException e) {
            log.warn("저장된 신청 가이드를 읽지 못해 다시 생성합니다. policyId={}", policyId);
            return Optional.empty();
        }
    }

    private static String canonicalize(String value) {
        String text = value == null ? "" : value;
        int byteLength = text.getBytes(StandardCharsets.UTF_8).length;
        return byteLength + ":" + text + ";";
    }
}
