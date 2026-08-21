package com.mozip.server.chat.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.chat.domain.ChatCondition;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.recommendation.domain.ConditionResult;
import com.mozip.server.recommendation.domain.ConditionStatus;
import com.mozip.server.recommendation.domain.ConditionType;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import com.mozip.server.recommendation.evaluator.EligibilityConditionMatcher;
import com.mozip.server.region.entity.Region;
import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.Gender;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ChatConditionEvaluatorTest {

    private final ChatConditionEvaluator chatConditionEvaluator =
            new ChatConditionEvaluator(new EligibilityConditionMatcher());

    @Test
    void Eligibility가_없으면_전체_NEEDS_REVIEW이고_조건_리스트는_비어있다() {
        ChatCondition condition = fullCondition();

        PolicyEligibilityResult result =
                chatConditionEvaluator.evaluate(condition, nationalPolicy(), List.of(), null, null);

        assertThat(result.overallStatus()).isEqualTo(EligibilityStatus.NEEDS_REVIEW);
        assertThat(result.overallReason()).isEqualTo("자격 조건 정보가 등록되지 않음");
        assertThat(result.conditionResults()).isEmpty();
    }

    @Test
    void 모든_조건을_충족하면_ELIGIBLE이다() {
        ChatCondition condition = fullCondition();
        PolicyEligibility eligibility = fullEligibility();

        PolicyEligibilityResult result =
                chatConditionEvaluator.evaluate(condition, nationalPolicy(), List.of(), eligibility, null);

        assertThat(result.overallStatus()).isEqualTo(EligibilityStatus.ELIGIBLE);
        assertThat(result.conditionResults()).hasSize(5);
        assertThat(result.conditionResults()).allMatch(r -> r.status() == ConditionStatus.MATCHED);
    }

    @Test
    void 나이_조건이_불일치하면_INELIGIBLE이다() {
        ChatCondition condition = new ChatCondition(15, null, EmploymentStatus.JOB_SEEKER, HouseholdType.SINGLE,
                IncomeType.MEDIAN_PERCENTAGE, 80);
        PolicyEligibility eligibility = fullEligibility();

        PolicyEligibilityResult result =
                chatConditionEvaluator.evaluate(condition, nationalPolicy(), List.of(), eligibility, null);

        assertThat(conditionOf(result, ConditionType.AGE).status()).isEqualTo(ConditionStatus.NOT_MATCHED);
        assertThat(result.overallStatus()).isEqualTo(EligibilityStatus.INELIGIBLE);
    }

    @Test
    void 소득_조건이_불일치하면_INELIGIBLE이다() {
        ChatCondition condition = new ChatCondition(28, null, EmploymentStatus.JOB_SEEKER, HouseholdType.SINGLE,
                IncomeType.MEDIAN_PERCENTAGE, 999);
        PolicyEligibility eligibility = fullEligibility();

        PolicyEligibilityResult result =
                chatConditionEvaluator.evaluate(condition, nationalPolicy(), List.of(), eligibility, null);

        assertThat(conditionOf(result, ConditionType.INCOME).status()).isEqualTo(ConditionStatus.NOT_MATCHED);
        assertThat(result.overallStatus()).isEqualTo(EligibilityStatus.INELIGIBLE);
    }

    @Test
    void employmentStatus가_불일치하면_INELIGIBLE이다() {
        ChatCondition condition = new ChatCondition(28, null, EmploymentStatus.EMPLOYED, HouseholdType.SINGLE,
                IncomeType.MEDIAN_PERCENTAGE, 80);
        PolicyEligibility eligibility = fullEligibility();

        PolicyEligibilityResult result =
                chatConditionEvaluator.evaluate(condition, nationalPolicy(), List.of(), eligibility, null);

        assertThat(conditionOf(result, ConditionType.EMPLOYMENT_STATUS).status()).isEqualTo(ConditionStatus.NOT_MATCHED);
        assertThat(result.overallStatus()).isEqualTo(EligibilityStatus.INELIGIBLE);
    }

    @Test
    void householdType이_불일치하면_INELIGIBLE이다() {
        ChatCondition condition = new ChatCondition(28, null, EmploymentStatus.JOB_SEEKER, HouseholdType.ELDERLY,
                IncomeType.MEDIAN_PERCENTAGE, 80);
        PolicyEligibility eligibility = fullEligibility();

        PolicyEligibilityResult result =
                chatConditionEvaluator.evaluate(condition, nationalPolicy(), List.of(), eligibility, null);

        assertThat(conditionOf(result, ConditionType.HOUSEHOLD_TYPE).status()).isEqualTo(ConditionStatus.NOT_MATCHED);
        assertThat(result.overallStatus()).isEqualTo(EligibilityStatus.INELIGIBLE);
    }

    @Test
    void 조건_정보가_부족하면_기존_정책_판정_기준에_따라_NEEDS_REVIEW다() {
        ChatCondition condition = new ChatCondition(null, null, null, null, null, null);
        PolicyEligibility eligibility = fullEligibility();

        PolicyEligibilityResult result =
                chatConditionEvaluator.evaluate(condition, nationalPolicy(), List.of(), eligibility, null);

        assertThat(result.overallStatus()).isEqualTo(EligibilityStatus.NEEDS_REVIEW);
        assertThat(conditionOf(result, ConditionType.AGE).status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
    }

    @Test
    void gender와_additionalConditions은_평가_대상에_포함되지_않는다() {
        ChatCondition condition = fullCondition();
        PolicyEligibility eligibility = PolicyEligibility.builder()
                .genderCondition(Gender.FEMALE)
                .additionalConditions(Map.of("note", "추가 서류 필요"))
                .build();

        PolicyEligibilityResult result =
                chatConditionEvaluator.evaluate(condition, nationalPolicy(), List.of(), eligibility, null);

        assertThat(result.conditionResults()).noneMatch(r -> r.type() == ConditionType.GENDER);
        assertThat(result.conditionResults()).noneMatch(r -> r.type() == ConditionType.ADDITIONAL_CONDITIONS);
    }

    @Test
    void 지역_정책에서_전달받은_userRegion이_상위_지역과_일치하면_MATCHED다() {
        Region seoul = region(1L, "SEOUL", null);
        Region mapo = region(2L, "SEOUL_MAPO", seoul);

        ChatCondition condition = new ChatCondition(28, mapo.getId(), EmploymentStatus.JOB_SEEKER, HouseholdType.SINGLE,
                IncomeType.MEDIAN_PERCENTAGE, 80);
        PolicyEligibility eligibility = fullEligibility();
        Policy regionalPolicy = Policy.builder()
                .title("지역 테스트 정책")
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(RegionScope.REGIONAL)
                .status(PolicyStatus.OPEN)
                .build();

        PolicyEligibilityResult result =
                chatConditionEvaluator.evaluate(condition, regionalPolicy, List.of(seoul.getId()), eligibility, mapo);

        assertThat(conditionOf(result, ConditionType.REGION).status()).isEqualTo(ConditionStatus.MATCHED);
    }

    private ChatCondition fullCondition() {
        return new ChatCondition(28, null, EmploymentStatus.JOB_SEEKER, HouseholdType.SINGLE,
                IncomeType.MEDIAN_PERCENTAGE, 80);
    }

    private PolicyEligibility fullEligibility() {
        return PolicyEligibility.builder()
                .minimumAge(20).maximumAge(40)
                .incomeType(IncomeType.MEDIAN_PERCENTAGE).maximumIncomeValue(100)
                .allowedEmploymentStatuses(List.of("JOB_SEEKER"))
                .allowedHouseholdTypes(List.of("SINGLE"))
                .build();
    }

    private Policy nationalPolicy() {
        return Policy.builder()
                .title("전국 테스트 정책")
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.OPEN)
                .build();
    }

    private ConditionResult conditionOf(PolicyEligibilityResult result, ConditionType type) {
        return result.conditionResults().stream()
                .filter(r -> r.type() == type)
                .findFirst()
                .orElseThrow();
    }

    private Region region(Long id, String code, Region parent) {
        Region region = Region.builder().code(code).name(code).parent(parent).build();
        ReflectionTestUtils.setField(region, "id", id);
        return region;
    }
}
