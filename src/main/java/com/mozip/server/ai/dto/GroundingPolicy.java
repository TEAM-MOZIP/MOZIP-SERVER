package com.mozip.server.ai.dto;

import com.mozip.server.recommendation.domain.EligibilityStatus;
import java.time.LocalDate;

/**
 * @param summary 정책 요약(한 줄 설명). AI가 정책 목록을 안내할 때 정책별로 간단히 설명하는 데 쓴다. 없으면 null.
 */
public record GroundingPolicy(
        Long policyId,
        String title,
        EligibilityStatus eligibilityStatus,
        LocalDate applicationEndDate,
        String summary
) {
}
