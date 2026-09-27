package com.mozip.server.ai.dto;

import com.mozip.server.recommendation.domain.ConditionResult;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import java.util.List;

/**
 * 특정 정책 Q&A(Case C)에서 AI에 넘기는 정책 상세 근거.
 *
 * @param benefit           지원내용(무엇을 얼마나 받는지). 없으면 null
 * @param applicationMethod 신청 방법·절차. 없으면 null
 * @param requiredDocuments 준비 서류. 없으면 null
 * @param contact           문의처. 없으면 null
 * @param applicationUrl    온라인 신청 주소. 없으면 null
 * @param policyId          정책 id(AI가 정책 카드 블록에 쓴다)
 * @param eligibilityStatus 사용자가 말한 조건으로 판정한 자격. 조건을 말하지 않았으면 null
 * @param conditionResults  조건별 판정 결과. 조건을 말하지 않았으면 빈 목록
 * @param applicationGuideAttached 신청 절차·준비 서류를 SERVER가 캐시된 신청 가이드로 붙이면 true
 *                                 (AI는 신청 방법 답변에서 단계·서류를 다시 쓰지 않는다)
 */
public record PolicyDetailGrounding(
        String title,
        String summary,
        String eligibility,
        String applicationPeriod,
        String organization,
        String benefit,
        String applicationMethod,
        String requiredDocuments,
        String contact,
        String applicationUrl,
        Long policyId,
        EligibilityStatus eligibilityStatus,
        List<ConditionResult> conditionResults,
        boolean applicationGuideAttached
) {

    public PolicyDetailGrounding {
        conditionResults = conditionResults != null ? List.copyOf(conditionResults) : List.of();
    }

    /** 정책 id·자격 판정 없이 만드는 생성자(하위 호환용). */
    public PolicyDetailGrounding(String title, String summary, String eligibility, String applicationPeriod,
                                 String organization, String benefit, String applicationMethod,
                                 String requiredDocuments, String contact, String applicationUrl) {
        this(title, summary, eligibility, applicationPeriod, organization, benefit, applicationMethod,
                requiredDocuments, contact, applicationUrl, null, null, List.of(), false);
    }

    /** 정책 id와 자격 판정을 붙인 복사본. */
    public PolicyDetailGrounding withPolicy(Long policyId, EligibilityStatus eligibilityStatus,
                                            List<ConditionResult> conditionResults) {
        return new PolicyDetailGrounding(title, summary, eligibility, applicationPeriod, organization, benefit,
                applicationMethod, requiredDocuments, contact, applicationUrl, policyId, eligibilityStatus,
                conditionResults, applicationGuideAttached);
    }

    /** 신청 가이드를 SERVER가 붙이는지 표시한 복사본. */
    public PolicyDetailGrounding withApplicationGuideAttached(boolean attached) {
        return new PolicyDetailGrounding(title, summary, eligibility, applicationPeriod, organization, benefit,
                applicationMethod, requiredDocuments, contact, applicationUrl, policyId, eligibilityStatus,
                conditionResults, attached);
    }

    /** 상세 항목 없이 만드는 기존 생성자(하위 호환용). */
    public PolicyDetailGrounding(String title, String summary, String eligibility, String applicationPeriod,
                                 String organization) {
        this(title, summary, eligibility, applicationPeriod, organization, null, null, null, null, null, null, null,
                List.of(), false);
    }
}
