package com.mozip.server.chat.domain;

import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;

/**
 * 챗봇이 대화에서 추출한 조건을 담는 일시적인(transient) 값 객체다. 로그인 사용자의 UserProfile을 조회하거나
 * 병합/저장하지 않고, 이 요청 한 번의 정책 탐색에만 사용한다. gender는 eligibility 자동 판정 대상이 아니므로
 * 포함하지 않는다.
 */
public record ChatCondition(
        Integer age,
        Long regionId,
        EmploymentStatus employmentStatus,
        HouseholdType householdType,
        IncomeType incomeType,
        Integer incomeValue
) {
}
