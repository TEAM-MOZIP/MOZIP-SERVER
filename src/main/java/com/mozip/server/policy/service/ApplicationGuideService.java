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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ApplicationGuideService {

    private static final String APPLICATION_METHOD_STEP_TITLE = "신청 방법";

    private final PolicyRepository policyRepository;
    private final PolicyEligibilityRepository policyEligibilityRepository;
    private final PolicyApplicationInfoRepository policyApplicationInfoRepository;
    private final PolicyAvailabilityEvaluator policyAvailabilityEvaluator;
    private final PolicyApplicationGuideService policyApplicationGuideService;

    public ApplicationGuideService(PolicyRepository policyRepository,
                                    PolicyEligibilityRepository policyEligibilityRepository,
                                    PolicyApplicationInfoRepository policyApplicationInfoRepository,
                                    PolicyAvailabilityEvaluator policyAvailabilityEvaluator,
                                    PolicyApplicationGuideService policyApplicationGuideService) {
        this.policyRepository = policyRepository;
        this.policyEligibilityRepository = policyEligibilityRepository;
        this.policyApplicationInfoRepository = policyApplicationInfoRepository;
        this.policyAvailabilityEvaluator = policyAvailabilityEvaluator;
        this.policyApplicationGuideService = policyApplicationGuideService;
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
            } else {
                com.mozip.server.ai.dto.ApplicationGuideResponse aiResult = policyApplicationGuideService.generate(
                        applicationInstructionsSource, applicationInfo.getRequiredDocumentsText());
                steps = aiResult.steps().stream().map(ApplicationGuideStepResponse::from).toList();
                requiredDocuments = aiResult.requiredDocuments();
            }
        }

        return ApplicationGuideResponse.from(policy, eligibility, availabilityResult, applicationInfo, steps,
                requiredDocuments);
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

    private List<String> buildRawRequiredDocuments(String requiredDocumentsText) {
        if (requiredDocumentsText == null || requiredDocumentsText.isBlank()) {
            return List.of();
        }
        return List.of(requiredDocumentsText);
    }
}
