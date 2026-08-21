package com.mozip.server.chat.evaluator;

import com.mozip.server.chat.domain.ChatCondition;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.recommendation.domain.ConditionResult;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import com.mozip.server.recommendation.evaluator.EligibilityConditionMatcher;
import com.mozip.server.region.entity.Region;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ChatCondition을 기존 {@link com.mozip.server.recommendation.evaluator.PolicyEligibilityEvaluator}와
 * 동일한 판정 기준({@link EligibilityConditionMatcher})으로 평가한다. UserProfile은 사용하지 않으며,
 * gender/additionalConditions은 v1 챗봇 eligibility 평가 대상이 아니므로 판정하지 않는다. userRegion은
 * 호출자가 미리 resolve해서 전달한다 — 정책 개수만큼 반복 조회하지 않기 위함이다.
 */
@Component
public class ChatConditionEvaluator {

    private static final String NO_ELIGIBILITY_REASON = "자격 조건 정보가 등록되지 않음";

    private final EligibilityConditionMatcher matcher;

    public ChatConditionEvaluator(EligibilityConditionMatcher matcher) {
        this.matcher = matcher;
    }

    public PolicyEligibilityResult evaluate(ChatCondition condition, Policy policy, List<Long> policyRegionIds,
                                             PolicyEligibility eligibility, Region userRegion) {
        if (eligibility == null) {
            return new PolicyEligibilityResult(EligibilityStatus.NEEDS_REVIEW, NO_ELIGIBILITY_REASON, List.of());
        }

        List<ConditionResult> results = new ArrayList<>();
        results.add(matcher.matchAge(condition.age(), eligibility.getMinimumAge(), eligibility.getMaximumAge()));
        results.add(matcher.matchRegion(policy.getRegionScope(), policyRegionIds, userRegion));
        results.add(matcher.matchIncome(eligibility.getIncomeType(), eligibility.getMinimumIncomeValue(),
                eligibility.getMaximumIncomeValue(), condition.incomeType(), condition.incomeValue()));
        results.add(matcher.matchEmploymentStatus(eligibility.getAllowedEmploymentStatuses(), condition.employmentStatus()));
        results.add(matcher.matchHouseholdType(eligibility.getAllowedHouseholdTypes(), condition.householdType()));

        return PolicyEligibilityResult.summarize(results);
    }
}
