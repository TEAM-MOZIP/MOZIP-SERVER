package com.mozip.server.recommendation.evaluator;

import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.recommendation.domain.ConditionResult;
import com.mozip.server.recommendation.domain.ConditionStatus;
import com.mozip.server.recommendation.domain.ConditionType;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import com.mozip.server.user.entity.IncomeType;
import com.mozip.server.user.entity.UserProfile;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PolicyEligibilityEvaluator {

    private static final String NO_ELIGIBILITY_REASON = "자격 조건 정보가 등록되지 않음";
    private static final String ELIGIBLE_REASON = "모든 자동 판정 조건을 충족했습니다.";
    private static final String INELIGIBLE_REASON = "충족하지 못한 자격 조건이 있습니다.";
    private static final String NEEDS_REVIEW_REASON = "자동 판정할 수 없는 조건이 있습니다.";

    private final Clock clock;

    public PolicyEligibilityEvaluator(Clock clock) {
        this.clock = clock;
    }

    public PolicyEligibilityResult evaluate(UserProfile userProfile, Policy policy, List<Long> policyRegionIds,
                                             PolicyEligibility eligibility) {
        if (eligibility == null) {
            return new PolicyEligibilityResult(EligibilityStatus.NEEDS_REVIEW, NO_ELIGIBILITY_REASON, List.of());
        }

        List<ConditionResult> results = new ArrayList<>();
        results.add(evaluateAge(userProfile, eligibility));
        results.add(evaluateRegion(userProfile, policy, policyRegionIds));
        results.add(evaluateIncome(userProfile, eligibility));
        results.add(evaluateEmploymentStatus(userProfile, eligibility));
        results.add(evaluateHouseholdType(userProfile, eligibility));

        if (eligibility.getGenderCondition() != null) {
            results.add(new ConditionResult(ConditionType.GENDER, ConditionStatus.NEEDS_REVIEW,
                    "성별 조건 자동 판정 미지원"));
        }
        if (eligibility.getAdditionalConditions() != null && !eligibility.getAdditionalConditions().isEmpty()) {
            results.add(new ConditionResult(ConditionType.ADDITIONAL_CONDITIONS, ConditionStatus.NEEDS_REVIEW,
                    "추가 확인이 필요한 조건이 있습니다"));
        }

        return summarize(results);
    }

    private PolicyEligibilityResult summarize(List<ConditionResult> results) {
        boolean hasNotMatched = results.stream().anyMatch(r -> r.status() == ConditionStatus.NOT_MATCHED);
        boolean hasNeedsReview = results.stream().anyMatch(r -> r.status() == ConditionStatus.NEEDS_REVIEW);

        if (hasNotMatched) {
            return new PolicyEligibilityResult(EligibilityStatus.INELIGIBLE, INELIGIBLE_REASON, results);
        }
        if (hasNeedsReview) {
            return new PolicyEligibilityResult(EligibilityStatus.NEEDS_REVIEW, NEEDS_REVIEW_REASON, results);
        }
        return new PolicyEligibilityResult(EligibilityStatus.ELIGIBLE, ELIGIBLE_REASON, results);
    }

    private ConditionResult evaluateAge(UserProfile userProfile, PolicyEligibility eligibility) {
        Integer minimumAge = eligibility.getMinimumAge();
        Integer maximumAge = eligibility.getMaximumAge();

        if (minimumAge == null && maximumAge == null) {
            return new ConditionResult(ConditionType.AGE, ConditionStatus.MATCHED, "연령 제한 없음");
        }
        if (minimumAge != null && maximumAge != null && minimumAge > maximumAge) {
            return new ConditionResult(ConditionType.AGE, ConditionStatus.NEEDS_REVIEW, "연령 조건 데이터가 올바르지 않음");
        }

        LocalDate birthDate = userProfile.getBirthDate();
        if (birthDate == null) {
            return new ConditionResult(ConditionType.AGE, ConditionStatus.NEEDS_REVIEW, "생년월일 정보가 없어 자동 판정할 수 없음");
        }

        LocalDate today = LocalDate.now(clock);
        if (birthDate.isAfter(today)) {
            return new ConditionResult(ConditionType.AGE, ConditionStatus.NEEDS_REVIEW, "생년월일 정보가 유효하지 않음");
        }

        int age = Period.between(birthDate, today).getYears();
        boolean satisfiesMin = minimumAge == null || age >= minimumAge;
        boolean satisfiesMax = maximumAge == null || age <= maximumAge;

        if (satisfiesMin && satisfiesMax) {
            return new ConditionResult(ConditionType.AGE, ConditionStatus.MATCHED, "연령 조건을 충족합니다.");
        }
        return new ConditionResult(ConditionType.AGE, ConditionStatus.NOT_MATCHED, "연령 조건을 충족하지 못했습니다.");
    }

    private ConditionResult evaluateRegion(UserProfile userProfile, Policy policy, List<Long> policyRegionIds) {
        if (policy.getRegionScope() == RegionScope.NATIONAL) {
            return new ConditionResult(ConditionType.REGION, ConditionStatus.MATCHED, "전국 대상 정책입니다.");
        }
        if (policyRegionIds.isEmpty()) {
            return new ConditionResult(ConditionType.REGION, ConditionStatus.NEEDS_REVIEW, "정책 지원 지역 정보가 등록되지 않음");
        }
        Long userRegionId = userProfile.getRegion().getId();
        if (policyRegionIds.contains(userRegionId)) {
            return new ConditionResult(ConditionType.REGION, ConditionStatus.MATCHED, "지역 조건을 충족합니다.");
        }
        return new ConditionResult(ConditionType.REGION, ConditionStatus.NOT_MATCHED, "정책 지원 지역과 일치하지 않습니다.");
    }

    private ConditionResult evaluateIncome(UserProfile userProfile, PolicyEligibility eligibility) {
        IncomeType incomeType = eligibility.getIncomeType();
        Integer minimumIncomeValue = eligibility.getMinimumIncomeValue();
        Integer maximumIncomeValue = eligibility.getMaximumIncomeValue();

        if (incomeType == null && minimumIncomeValue == null && maximumIncomeValue == null) {
            return new ConditionResult(ConditionType.INCOME, ConditionStatus.MATCHED, "소득 제한 없음");
        }
        if (incomeType == null) {
            return new ConditionResult(ConditionType.INCOME, ConditionStatus.NEEDS_REVIEW,
                    "소득 기준 유형이 없어 자동 판정할 수 없음");
        }
        if (minimumIncomeValue == null && maximumIncomeValue == null) {
            return new ConditionResult(ConditionType.INCOME, ConditionStatus.NEEDS_REVIEW,
                    "소득 기준값이 없어 자동 판정할 수 없음");
        }
        if (minimumIncomeValue != null && maximumIncomeValue != null && minimumIncomeValue > maximumIncomeValue) {
            return new ConditionResult(ConditionType.INCOME, ConditionStatus.NEEDS_REVIEW,
                    "소득 조건 데이터가 올바르지 않음");
        }
        if (incomeType != userProfile.getIncomeType()) {
            return new ConditionResult(ConditionType.INCOME, ConditionStatus.NEEDS_REVIEW,
                    "소득 산정 기준이 달라 직접 비교할 수 없습니다.");
        }

        Integer incomeValue = userProfile.getIncomeValue();
        if (incomeValue == null) {
            return new ConditionResult(ConditionType.INCOME, ConditionStatus.NEEDS_REVIEW,
                    "사용자 소득 정보가 없어 자동 판정할 수 없음");
        }
        boolean satisfiesMin = minimumIncomeValue == null || incomeValue >= minimumIncomeValue;
        boolean satisfiesMax = maximumIncomeValue == null || incomeValue <= maximumIncomeValue;

        if (satisfiesMin && satisfiesMax) {
            return new ConditionResult(ConditionType.INCOME, ConditionStatus.MATCHED, "소득 조건을 충족합니다.");
        }
        return new ConditionResult(ConditionType.INCOME, ConditionStatus.NOT_MATCHED, "소득 조건을 충족하지 못했습니다.");
    }

    private ConditionResult evaluateEmploymentStatus(UserProfile userProfile, PolicyEligibility eligibility) {
        List<String> allowedEmploymentStatuses = eligibility.getAllowedEmploymentStatuses();
        // 시드 데이터에는 null과 빈 배열을 구분해서 쓰는 사례가 없어, 둘 다 "제한 없음"으로 동일하게 처리한다.
        if (allowedEmploymentStatuses == null || allowedEmploymentStatuses.isEmpty()) {
            return new ConditionResult(ConditionType.EMPLOYMENT_STATUS, ConditionStatus.MATCHED, "고용 상태 제한 없음");
        }
        if (allowedEmploymentStatuses.contains(userProfile.getEmploymentStatus().name())) {
            return new ConditionResult(ConditionType.EMPLOYMENT_STATUS, ConditionStatus.MATCHED, "고용 상태 조건을 충족합니다.");
        }
        return new ConditionResult(ConditionType.EMPLOYMENT_STATUS, ConditionStatus.NOT_MATCHED,
                "고용 상태 조건을 충족하지 못했습니다.");
    }

    private ConditionResult evaluateHouseholdType(UserProfile userProfile, PolicyEligibility eligibility) {
        List<String> allowedHouseholdTypes = eligibility.getAllowedHouseholdTypes();
        // 시드 데이터에는 null과 빈 배열을 구분해서 쓰는 사례가 없어, 둘 다 "제한 없음"으로 동일하게 처리한다.
        if (allowedHouseholdTypes == null || allowedHouseholdTypes.isEmpty()) {
            return new ConditionResult(ConditionType.HOUSEHOLD_TYPE, ConditionStatus.MATCHED, "가구 유형 제한 없음");
        }
        if (allowedHouseholdTypes.contains(userProfile.getHouseholdType().name())) {
            return new ConditionResult(ConditionType.HOUSEHOLD_TYPE, ConditionStatus.MATCHED, "가구 유형 조건을 충족합니다.");
        }
        return new ConditionResult(ConditionType.HOUSEHOLD_TYPE, ConditionStatus.NOT_MATCHED,
                "가구 유형 조건을 충족하지 못했습니다.");
    }
}
