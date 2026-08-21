package com.mozip.server.recommendation.evaluator;

import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.recommendation.domain.ConditionResult;
import com.mozip.server.recommendation.domain.ConditionStatus;
import com.mozip.server.recommendation.domain.ConditionType;
import com.mozip.server.region.entity.Region;
import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * UserProfile 기반 평가({@link PolicyEligibilityEvaluator})와 챗봇 transient 조건 기반 평가
 * (ChatConditionEvaluator)가 공통으로 사용하는 축별 순수 판정 로직이다. 값 출처(UserProfile/ChatCondition)에는
 * 관여하지 않고, 이미 추출된 원시 값만으로 판정한다.
 */
@Component
public class EligibilityConditionMatcher {

    public ConditionResult matchAge(Integer age, Integer minimumAge, Integer maximumAge) {
        if (minimumAge == null && maximumAge == null) {
            return matched(ConditionType.AGE, "연령 제한 없음");
        }
        if (minimumAge != null && maximumAge != null && minimumAge > maximumAge) {
            return needsReview(ConditionType.AGE, "연령 조건 데이터가 올바르지 않음");
        }
        if (age == null) {
            return needsReview(ConditionType.AGE, "나이 정보가 없어 자동 판정할 수 없음");
        }
        boolean satisfiesMin = minimumAge == null || age >= minimumAge;
        boolean satisfiesMax = maximumAge == null || age <= maximumAge;
        if (satisfiesMin && satisfiesMax) {
            return matched(ConditionType.AGE, "연령 조건을 충족합니다.");
        }
        return notMatched(ConditionType.AGE, "연령 조건을 충족하지 못했습니다.");
    }

    public ConditionResult matchRegion(RegionScope regionScope, List<Long> policyRegionIds, Region userRegion) {
        if (regionScope == RegionScope.NATIONAL) {
            return matched(ConditionType.REGION, "전국 대상 정책입니다.");
        }
        if (policyRegionIds.isEmpty()) {
            return needsReview(ConditionType.REGION, "정책 지원 지역 정보가 등록되지 않음");
        }
        if (userRegion == null) {
            return needsReview(ConditionType.REGION, "사용자 지역 정보가 없어 자동 판정할 수 없음");
        }
        if (policyRegionIds.contains(userRegion.getId())) {
            return matched(ConditionType.REGION, "지역 조건을 충족합니다.");
        }
        Region userRegionParent = userRegion.getParent();
        if (userRegionParent != null && policyRegionIds.contains(userRegionParent.getId())) {
            return matched(ConditionType.REGION, "지역 조건을 충족합니다.");
        }
        return notMatched(ConditionType.REGION, "정책 지원 지역과 일치하지 않습니다.");
    }

    public ConditionResult matchIncome(IncomeType conditionIncomeType, Integer minimumIncomeValue,
                                        Integer maximumIncomeValue, IncomeType userIncomeType, Integer userIncomeValue) {
        if (conditionIncomeType == null && minimumIncomeValue == null && maximumIncomeValue == null) {
            return matched(ConditionType.INCOME, "소득 제한 없음");
        }
        if (conditionIncomeType == null) {
            return needsReview(ConditionType.INCOME, "소득 기준 유형이 없어 자동 판정할 수 없음");
        }
        if (minimumIncomeValue == null && maximumIncomeValue == null) {
            return needsReview(ConditionType.INCOME, "소득 기준값이 없어 자동 판정할 수 없음");
        }
        if (minimumIncomeValue != null && maximumIncomeValue != null && minimumIncomeValue > maximumIncomeValue) {
            return needsReview(ConditionType.INCOME, "소득 조건 데이터가 올바르지 않음");
        }
        if (conditionIncomeType != userIncomeType) {
            return needsReview(ConditionType.INCOME, "소득 산정 기준이 달라 직접 비교할 수 없습니다.");
        }
        if (userIncomeValue == null) {
            return needsReview(ConditionType.INCOME, "사용자 소득 정보가 없어 자동 판정할 수 없음");
        }
        boolean satisfiesMin = minimumIncomeValue == null || userIncomeValue >= minimumIncomeValue;
        boolean satisfiesMax = maximumIncomeValue == null || userIncomeValue <= maximumIncomeValue;
        if (satisfiesMin && satisfiesMax) {
            return matched(ConditionType.INCOME, "소득 조건을 충족합니다.");
        }
        return notMatched(ConditionType.INCOME, "소득 조건을 충족하지 못했습니다.");
    }

    public ConditionResult matchEmploymentStatus(List<String> allowedEmploymentStatuses,
                                                   EmploymentStatus employmentStatus) {
        if (allowedEmploymentStatuses == null || allowedEmploymentStatuses.isEmpty()) {
            return matched(ConditionType.EMPLOYMENT_STATUS, "고용 상태 제한 없음");
        }
        if (employmentStatus == null) {
            return needsReview(ConditionType.EMPLOYMENT_STATUS, "사용자 고용 상태 정보가 없어 자동 판정할 수 없음");
        }
        if (allowedEmploymentStatuses.contains(employmentStatus.name())) {
            return matched(ConditionType.EMPLOYMENT_STATUS, "고용 상태 조건을 충족합니다.");
        }
        return notMatched(ConditionType.EMPLOYMENT_STATUS, "고용 상태 조건을 충족하지 못했습니다.");
    }

    public ConditionResult matchHouseholdType(List<String> allowedHouseholdTypes, HouseholdType householdType) {
        if (allowedHouseholdTypes == null || allowedHouseholdTypes.isEmpty()) {
            return matched(ConditionType.HOUSEHOLD_TYPE, "가구 유형 제한 없음");
        }
        if (householdType == null) {
            return needsReview(ConditionType.HOUSEHOLD_TYPE, "사용자 가구 유형 정보가 없어 자동 판정할 수 없음");
        }
        if (allowedHouseholdTypes.contains(householdType.name())) {
            return matched(ConditionType.HOUSEHOLD_TYPE, "가구 유형 조건을 충족합니다.");
        }
        return notMatched(ConditionType.HOUSEHOLD_TYPE, "가구 유형 조건을 충족하지 못했습니다.");
    }

    private ConditionResult matched(ConditionType type, String reason) {
        return new ConditionResult(type, ConditionStatus.MATCHED, reason);
    }

    private ConditionResult notMatched(ConditionType type, String reason) {
        return new ConditionResult(type, ConditionStatus.NOT_MATCHED, reason);
    }

    private ConditionResult needsReview(ConditionType type, String reason) {
        return new ConditionResult(type, ConditionStatus.NEEDS_REVIEW, reason);
    }
}
