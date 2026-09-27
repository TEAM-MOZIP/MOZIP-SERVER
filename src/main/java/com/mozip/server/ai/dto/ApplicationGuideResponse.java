package com.mozip.server.ai.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;

/**
 * @param fallback AI 생성에 실패해 원문을 그대로 한 단계로 담은 결과면 true. AI 응답 JSON에는 없는 값이라
 *                 역직렬화하면 false가 된다. 원문 대체 결과는 캐시하지 않기 위해 구분한다.
 */
public record ApplicationGuideResponse(
        List<ApplicationGuideStep> steps,
        List<String> requiredDocuments,
        boolean fallback
) {

    @JsonCreator
    public ApplicationGuideResponse {
    }

    public ApplicationGuideResponse(List<ApplicationGuideStep> steps, List<String> requiredDocuments) {
        this(steps, requiredDocuments, false);
    }
}
