package com.mozip.server.ai.dto;

/**
 * 특정 정책 Q&A(Case C)에서 AI에 넘기는 정책 상세 근거.
 *
 * @param benefit           지원내용(무엇을 얼마나 받는지). 없으면 null
 * @param applicationMethod 신청 방법·절차. 없으면 null
 * @param requiredDocuments 준비 서류. 없으면 null
 * @param contact           문의처. 없으면 null
 * @param applicationUrl    온라인 신청 주소. 없으면 null
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
        String applicationUrl
) {

    /** 상세 항목 없이 만드는 기존 생성자(하위 호환용). */
    public PolicyDetailGrounding(String title, String summary, String eligibility, String applicationPeriod,
                                 String organization) {
        this(title, summary, eligibility, applicationPeriod, organization, null, null, null, null, null);
    }
}
