package com.mozip.server.ai.dto;

import com.mozip.server.recommendation.domain.EligibilityStatus;
import java.time.LocalDate;

/**
 * @param summary 정책 요약(한 줄 설명). AI가 정책 목록을 안내할 때 정책별로 간단히 설명하는 데 쓴다. 없으면 null.
 * @param target  지원 대상 원문 앞부분. AI가 추천 이유·비교표를 쓸 때 근거로 쓴다. 없으면 null.
 * @param benefit 지원 내용 원문 앞부분. AI가 금액·기간 같은 핵심 정보를 뽑을 때 근거로 쓴다. 없으면 null.
 */
public record GroundingPolicy(
        Long policyId,
        String title,
        EligibilityStatus eligibilityStatus,
        LocalDate applicationEndDate,
        String summary,
        String target,
        String benefit
) {

    /** 지원 대상·내용 없이 만드는 생성자(하위 호환용). */
    public GroundingPolicy(Long policyId, String title, EligibilityStatus eligibilityStatus,
                           LocalDate applicationEndDate, String summary) {
        this(policyId, title, eligibilityStatus, applicationEndDate, summary, null, null);
    }
}
