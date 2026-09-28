package com.mozip.server.ai.dto;

/**
 * 서버가 추출한 사용자 조건 — AI가 "이 조건으로 정책을 찾았다"고 언급하거나 자격 판단에 활용할 수 있도록 넘겨준다.
 * 값이 null이면 해당 조건은 사용자가 말하지 않은 것이다.
 */
public record UserConditionGrounding(
        Integer age,
        String region,           // 지역명 (예: "서울특별시 강남구")
        String employmentStatus, // "EMPLOYED" | "UNEMPLOYED" | "JOB_SEEKER"
        String householdType,    // "SINGLE" | "ELDERLY" | "SINGLE_PARENT" | "DISABLED"
        String incomeType,       // "ABSOLUTE" | "MEDIAN_PERCENTAGE"
        Integer incomeValue      // ABSOLUTE: 만 원 단위 금액 / MEDIAN_PERCENTAGE: 기준 중위소득 %
) {
}
