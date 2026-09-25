package com.mozip.server.policy.service;

import com.mozip.server.ai.service.PolicyApplicationGuideService;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.dto.ApplicationGuideResponse;
import com.mozip.server.policy.dto.ApplicationGuideStepResponse;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyApplicationInfo;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.evaluator.PolicyAvailabilityEvaluator;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyApplicationInfoRepository;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ApplicationGuideService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationGuideService.class);
    private static final String APPLICATION_METHOD_STEP_TITLE = "신청 방법";
    private static final String RAW_PROCEDURE_STEP_TITLE = "신청 절차";
    /**
     * 신청 절차·준비 서류 원문이 이보다 길면 AI 가이드 생성이 timeout(운영 nginx 15초 이내)을 넘기기 쉽다.
     * 원문을 잘라 보내면 뒤쪽 절차가 빠질 수 있으므로, 이 경우엔 AI를 거치지 않고 원문을 그대로 보여준다.
     */
    static final int MAX_AI_SOURCE_LENGTH = 1500;
    private static final Pattern MEANINGFUL_TEXT = Pattern.compile("[\\p{L}\\p{N}]");

    private final PolicyRepository policyRepository;
    private final PolicyEligibilityRepository policyEligibilityRepository;
    private final PolicyApplicationInfoRepository policyApplicationInfoRepository;
    private final PolicyAvailabilityEvaluator policyAvailabilityEvaluator;
    private final PolicyApplicationGuideService policyApplicationGuideService;
    private final PolicyApplicationGuideCacheService policyApplicationGuideCacheService;

    public ApplicationGuideService(PolicyRepository policyRepository,
                                    PolicyEligibilityRepository policyEligibilityRepository,
                                    PolicyApplicationInfoRepository policyApplicationInfoRepository,
                                    PolicyAvailabilityEvaluator policyAvailabilityEvaluator,
                                    PolicyApplicationGuideService policyApplicationGuideService,
                                    PolicyApplicationGuideCacheService policyApplicationGuideCacheService) {
        this.policyRepository = policyRepository;
        this.policyEligibilityRepository = policyEligibilityRepository;
        this.policyApplicationInfoRepository = policyApplicationInfoRepository;
        this.policyAvailabilityEvaluator = policyAvailabilityEvaluator;
        this.policyApplicationGuideService = policyApplicationGuideService;
        this.policyApplicationGuideCacheService = policyApplicationGuideCacheService;
    }

    public ApplicationGuideResponse getApplicationGuide(Long policyId) {
        Policy policy = policyRepository.findWithOrganizationById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException(policyId));
        PolicyEligibility eligibility = policyEligibilityRepository.findByPolicyId(policyId).orElse(null);
        PolicyApplicationInfo applicationInfo = policyApplicationInfoRepository.findByPolicyId(policyId).orElse(null);
        PolicyAvailabilityResult availabilityResult = policyAvailabilityEvaluator.evaluate(policy);

        List<ApplicationGuideStepResponse> steps;
        List<String> requiredDocuments;

        if (applicationInfo == null) {
            steps = buildApplicationMethodOnlySteps(policy);
            requiredDocuments = List.of();
        } else {
            String applicationInstructionsSource = resolveApplicationInstructionsSource(policy, applicationInfo);
            if (applicationInstructionsSource == null || applicationInstructionsSource.isBlank()) {
                steps = List.of();
                requiredDocuments = buildRawRequiredDocuments(applicationInfo.getRequiredDocumentsText());
            } else if (isTooLongForAi(applicationInstructionsSource, applicationInfo.getRequiredDocumentsText())) {
                steps = List.of(new ApplicationGuideStepResponse(1, RAW_PROCEDURE_STEP_TITLE,
                        applicationInstructionsSource));
                requiredDocuments = buildRawRequiredDocuments(applicationInfo.getRequiredDocumentsText());
            } else {
                com.mozip.server.ai.dto.ApplicationGuideResponse guide = findOrGenerateGuide(
                        policyId, applicationInstructionsSource, applicationInfo.getRequiredDocumentsText());
                steps = guide.steps().stream().map(ApplicationGuideStepResponse::from).toList();
                requiredDocuments = guide.requiredDocuments();
            }
        }

        return ApplicationGuideResponse.from(policy, eligibility, availabilityResult, applicationInfo, steps,
                withoutPlaceholders(requiredDocuments));
    }

    /**
     * 저장된 AI 가이드가 있고 원문이 그대로면 재사용하고, 아니면 AI로 생성해 저장한다.
     * AI 생성에 실패한 원문 대체 결과는 저장하지 않아 다음 조회 때 다시 생성을 시도한다.
     */
    private com.mozip.server.ai.dto.ApplicationGuideResponse findOrGenerateGuide(
            Long policyId, String applicationInstructionsSource, String requiredDocumentsSource) {
        String sourceHash = PolicyApplicationGuideCacheService.sourceHash(
                applicationInstructionsSource, requiredDocumentsSource);
        return policyApplicationGuideCacheService.find(policyId, sourceHash)
                .orElseGet(() -> generateAndCache(policyId, applicationInstructionsSource, requiredDocumentsSource,
                        sourceHash));
    }

    private com.mozip.server.ai.dto.ApplicationGuideResponse generateAndCache(
            Long policyId, String applicationInstructionsSource, String requiredDocumentsSource, String sourceHash) {
        com.mozip.server.ai.dto.ApplicationGuideResponse generated =
                policyApplicationGuideService.generate(applicationInstructionsSource, requiredDocumentsSource);
        if (!generated.fallback()) {
            try {
                policyApplicationGuideCacheService.save(policyId, generated, sourceHash);
            } catch (DataAccessException e) {
                // 캐시 저장 실패(동시 저장 충돌 등)는 응답에 영향을 주지 않는다 — 다음 조회 때 다시 저장된다.
                log.warn("신청 가이드 캐시 저장에 실패했습니다. policyId={}, exceptionType={}",
                        policyId, e.getClass().getSimpleName());
            }
        }
        return generated;
    }

    private boolean isTooLongForAi(String applicationInstructionsSource, String requiredDocumentsSource) {
        return applicationInstructionsSource.length() > MAX_AI_SOURCE_LENGTH
                || (requiredDocumentsSource != null && requiredDocumentsSource.length() > MAX_AI_SOURCE_LENGTH);
    }

    private List<ApplicationGuideStepResponse> buildApplicationMethodOnlySteps(Policy policy) {
        String applicationMethod = policy.getApplicationMethod();
        if (applicationMethod == null || applicationMethod.isBlank()) {
            return List.of();
        }
        return List.of(new ApplicationGuideStepResponse(1, APPLICATION_METHOD_STEP_TITLE, applicationMethod));
    }

    private String resolveApplicationInstructionsSource(Policy policy, PolicyApplicationInfo applicationInfo) {
        String procedure = applicationInfo.getApplicationProcedure();
        if (procedure != null && !procedure.isBlank()) {
            return procedure;
        }
        return policy.getApplicationMethod();
    }

    /** 원문의 "-"처럼 글자·숫자가 하나도 없는 항목은 "없음" 표시이므로 준비 서류에서 뺀다. */
    private static List<String> withoutPlaceholders(List<String> requiredDocuments) {
        return requiredDocuments.stream()
                .filter(document -> document != null && MEANINGFUL_TEXT.matcher(document).find())
                .toList();
    }

    private List<String> buildRawRequiredDocuments(String requiredDocumentsText) {
        if (requiredDocumentsText == null || requiredDocumentsText.isBlank()) {
            return List.of();
        }
        return List.of(requiredDocumentsText);
    }
}
