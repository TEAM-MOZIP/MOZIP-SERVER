package com.mozip.server.recommendation.evaluator;

import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.recommendation.domain.ConditionResult;
import com.mozip.server.recommendation.domain.ConditionStatus;
import com.mozip.server.recommendation.domain.ConditionType;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
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

    private final Clock clock;
    private final EligibilityConditionMatcher matcher;

    public PolicyEligibilityEvaluator(Clock clock, EligibilityConditionMatcher matcher) {
        this.clock = clock;
        this.matcher = matcher;
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

        return PolicyEligibilityResult.summarize(results);
    }

    private ConditionResult evaluateAge(UserProfile userProfile, PolicyEligibility eligibility) {
        Integer minimumAge = eligibility.getMinimumAge();
        Integer maximumAge = eligibility.getMaximumAge();

        if (minimumAge == null && maximumAge == null) {
            return matcher.matchAge(null, null, null);
        }
        if (minimumAge != null && maximumAge != null && minimumAge > maximumAge) {
            return matcher.matchAge(null, minimumAge, maximumAge);
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
        return matcher.matchAge(age, minimumAge, maximumAge);
    }

    private ConditionResult evaluateRegion(UserProfile userProfile, Policy policy, List<Long> policyRegionIds) {
        return matcher.matchRegion(policy.getRegionScope(), policyRegionIds, userProfile.getRegion());
    }

    private ConditionResult evaluateIncome(UserProfile userProfile, PolicyEligibility eligibility) {
        return matcher.matchIncome(eligibility.getIncomeType(), eligibility.getMinimumIncomeValue(),
                eligibility.getMaximumIncomeValue(), userProfile.getIncomeType(), userProfile.getIncomeValue());
    }

    private ConditionResult evaluateEmploymentStatus(UserProfile userProfile, PolicyEligibility eligibility) {
        return matcher.matchEmploymentStatus(eligibility.getAllowedEmploymentStatuses(), userProfile.getEmploymentStatus());
    }

    private ConditionResult evaluateHouseholdType(UserProfile userProfile, PolicyEligibility eligibility) {
        return matcher.matchHouseholdType(eligibility.getAllowedHouseholdTypes(), userProfile.getHouseholdType());
    }
}
