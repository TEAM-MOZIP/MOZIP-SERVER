package com.mozip.server.ai.service;

import com.mozip.server.ai.client.ApplicationGuideClient;
import com.mozip.server.ai.dto.ApplicationGuideRequest;
import com.mozip.server.ai.dto.ApplicationGuideResponse;
import com.mozip.server.ai.dto.ApplicationGuideStep;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class PolicyApplicationGuideService {

    private static final Logger log = LoggerFactory.getLogger(PolicyApplicationGuideService.class);
    private static final String FALLBACK_STEP_TITLE = "신청 절차";

    private final ApplicationGuideClient applicationGuideClient;

    public PolicyApplicationGuideService(ApplicationGuideClient applicationGuideClient) {
        this.applicationGuideClient = applicationGuideClient;
    }

    public ApplicationGuideResponse generate(String applicationInstructionsSource, String requiredDocumentsSource) {
        ApplicationGuideRequest request =
                new ApplicationGuideRequest(applicationInstructionsSource, requiredDocumentsSource);

        ApplicationGuideResponse response;
        try {
            response = applicationGuideClient.generate(request);
        } catch (RestClientException e) {
            log.warn("신청 가이드 생성 호출에 실패해 원본 source 기반 가이드로 대체합니다. exceptionType={}",
                    e.getClass().getSimpleName());
            return rawFallback(applicationInstructionsSource, requiredDocumentsSource);
        }

        if (response == null || response.steps() == null || response.steps().isEmpty()) {
            log.warn("신청 가이드 생성 응답이 비어 있어 원본 source 기반 가이드로 대체합니다.");
            return rawFallback(applicationInstructionsSource, requiredDocumentsSource);
        }

        return response;
    }

    private ApplicationGuideResponse rawFallback(String applicationInstructionsSource, String requiredDocumentsSource) {
        List<ApplicationGuideStep> steps = List.of(
                new ApplicationGuideStep(1, FALLBACK_STEP_TITLE, applicationInstructionsSource));
        List<String> requiredDocuments = requiredDocumentsSource != null && !requiredDocumentsSource.isBlank()
                ? List.of(requiredDocumentsSource)
                : List.of();
        return new ApplicationGuideResponse(steps, requiredDocuments);
    }
}
