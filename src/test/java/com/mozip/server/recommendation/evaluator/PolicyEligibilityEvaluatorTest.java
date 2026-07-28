package com.mozip.server.recommendation.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.mozip.server.region.entity.Region;
import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;
import com.mozip.server.user.entity.UserProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PolicyEligibilityEvaluatorTest {

    // 평가 기준일: 2026-07-23 로 고정
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final PolicyEligibilityEvaluator evaluator = new PolicyEligibilityEvaluator(clock);

    private final Region seoulRegion = region(1L, "SEOUL", "서울특별시");
    private final Region busanRegion = region(2L, "BUSAN", "부산광역시");

    @Test
    void Eligibility가_없으면_전체_NEEDS_REVIEW이고_조건_리스트는_비어있다() {
        UserProfile userProfile = baseProfile().build();
        Policy policy = nationalPolicy();

        PolicyEligibilityResult result = evaluator.evaluate(userProfile, policy, List.of(), null);

        assertThat(result.overallStatus()).isEqualTo(EligibilityStatus.NEEDS_REVIEW);
        assertThat(result.overallReason()).isEqualTo("자격 조건 정보가 등록되지 않음");
        assertThat(result.conditionResults()).isEmpty();
    }

    @Test
    void 모든_조건을_충족하면_ELIGIBLE이다() {
        UserProfile userProfile = baseProfile().build();
        Policy policy = nationalPolicy();
        PolicyEligibility eligibility = baseEligibility()
                .minimumAge(20).maximumAge(40)
                .incomeType(IncomeType.MEDIAN_PERCENTAGE).maximumIncomeValue(100)
                .allowedEmploymentStatuses(List.of("JOB_SEEKER"))
                .allowedHouseholdTypes(List.of("SINGLE"))
                .build();

        PolicyEligibilityResult result = evaluator.evaluate(userProfile, policy, List.of(), eligibility);

        assertThat(result.overallStatus()).isEqualTo(EligibilityStatus.ELIGIBLE);
        assertThat(result.overallReason()).isEqualTo("모든 자동 판정 조건을 충족했습니다.");
        assertThat(result.conditionResults()).hasSize(5);
        assertThat(result.conditionResults()).allMatch(r -> r.status() == ConditionStatus.MATCHED);
    }

    @Test
    void 나이_조건이_없으면_MATCHED다() {
        UserProfile userProfile = baseProfile().build();
        PolicyEligibility eligibility = baseEligibility().build();

        ConditionResult age = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.AGE);

        assertThat(age.status()).isEqualTo(ConditionStatus.MATCHED);
    }

    @Test
    void 나이가_최소_경계값과_정확히_같으면_MATCHED다() {
        // 1998-05-14 기준 2026-07-23 시점 만 28세
        UserProfile userProfile = baseProfile().birthDate(LocalDate.of(1998, 5, 14)).build();
        PolicyEligibility eligibility = baseEligibility().minimumAge(28).maximumAge(28).build();

        ConditionResult age = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.AGE);

        assertThat(age.status()).isEqualTo(ConditionStatus.MATCHED);
    }

    @Test
    void 나이가_범위를_벗어나면_NOT_MATCHED다() {
        UserProfile userProfile = baseProfile().birthDate(LocalDate.of(1998, 5, 14)).build();
        PolicyEligibility eligibility = baseEligibility().minimumAge(29).build();

        ConditionResult age = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.AGE);

        assertThat(age.status()).isEqualTo(ConditionStatus.NOT_MATCHED);
    }

    @Test
    void 전국_정책이면_사용자_지역과_무관하게_MATCHED다() {
        UserProfile userProfile = baseProfile().region(busanRegion).build();
        PolicyEligibility eligibility = baseEligibility().build();

        ConditionResult region = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.REGION);

        assertThat(region.status()).isEqualTo(ConditionStatus.MATCHED);
    }

    @Test
    void 지역_정책이고_사용자_지역이_포함되면_MATCHED다() {
        UserProfile userProfile = baseProfile().region(seoulRegion).build();
        Policy policy = regionalPolicy();
        PolicyEligibility eligibility = baseEligibility().build();

        PolicyEligibilityResult result = evaluator.evaluate(userProfile, policy, List.of(seoulRegion.getId()), eligibility);

        assertThat(conditionOf(result, ConditionType.REGION).status()).isEqualTo(ConditionStatus.MATCHED);
    }

    @Test
    void 지역_정책이고_사용자_지역이_포함되지_않으면_NOT_MATCHED다() {
        UserProfile userProfile = baseProfile().region(busanRegion).build();
        Policy policy = regionalPolicy();
        PolicyEligibility eligibility = baseEligibility().build();

        PolicyEligibilityResult result = evaluator.evaluate(userProfile, policy, List.of(seoulRegion.getId()), eligibility);

        assertThat(conditionOf(result, ConditionType.REGION).status()).isEqualTo(ConditionStatus.NOT_MATCHED);
    }

    @Test
    void 지역_정책인데_policyRegionIds가_비어있으면_NEEDS_REVIEW다() {
        UserProfile userProfile = baseProfile().region(seoulRegion).build();
        PolicyEligibility eligibility = baseEligibility().build();

        ConditionResult region = evaluateSingle(userProfile, regionalPolicy(), eligibility, ConditionType.REGION);

        assertThat(region.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(region.reason()).isEqualTo("정책 지원 지역 정보가 등록되지 않음");
    }

    @Test
    void 지역_정책인데_사용자_지역이_null이면_NEEDS_REVIEW이고_NPE가_발생하지_않는다() {
        UserProfile userProfile = baseProfile().region(null).build();
        PolicyEligibility eligibility = baseEligibility().build();

        PolicyEligibilityResult result = evaluator.evaluate(userProfile, regionalPolicy(), List.of(seoulRegion.getId()), eligibility);

        ConditionResult region = conditionOf(result, ConditionType.REGION);
        assertThat(region.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(region.reason()).isEqualTo("사용자 지역 정보가 없어 자동 판정할 수 없음");
    }

    @Test
    void 전국_정책이면_사용자_지역이_null이어도_MATCHED다() {
        UserProfile userProfile = baseProfile().region(null).build();
        PolicyEligibility eligibility = baseEligibility().build();

        ConditionResult region = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.REGION);

        assertThat(region.status()).isEqualTo(ConditionStatus.MATCHED);
    }

    @Test
    void 생년월일이_평가_기준일보다_미래이면_NEEDS_REVIEW다() {
        UserProfile userProfile = baseProfile().birthDate(LocalDate.of(2030, 1, 1)).build();
        PolicyEligibility eligibility = baseEligibility().minimumAge(20).build();

        ConditionResult age = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.AGE);

        assertThat(age.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(age.reason()).isEqualTo("생년월일 정보가 유효하지 않음");
    }

    @Test
    void 최소연령이_최대연령보다_크면_NEEDS_REVIEW다() {
        UserProfile userProfile = baseProfile().build();
        PolicyEligibility eligibility = baseEligibility().minimumAge(50).maximumAge(20).build();

        ConditionResult age = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.AGE);

        assertThat(age.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(age.reason()).isEqualTo("연령 조건 데이터가 올바르지 않음");
    }

    @Test
    void 연령_제한이_없으면_birthDate가_미래여도_MATCHED다() {
        UserProfile userProfile = baseProfile().birthDate(LocalDate.of(2030, 1, 1)).build();
        PolicyEligibility eligibility = baseEligibility().build();

        ConditionResult age = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.AGE);

        assertThat(age.status()).isEqualTo(ConditionStatus.MATCHED);
        assertThat(age.reason()).isEqualTo("연령 제한 없음");
    }

    @Test
    void 연령_제한이_없으면_birthDate가_null이어도_MATCHED다() {
        UserProfile userProfile = baseProfile().birthDate(null).build();
        PolicyEligibility eligibility = baseEligibility().build();

        ConditionResult age = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.AGE);

        assertThat(age.status()).isEqualTo(ConditionStatus.MATCHED);
        assertThat(age.reason()).isEqualTo("연령 제한 없음");
    }

    @Test
    void 연령_범위가_역전되고_birthDate도_미래이면_정책_데이터_이상_사유가_먼저_반환된다() {
        UserProfile userProfile = baseProfile().birthDate(LocalDate.of(2030, 1, 1)).build();
        PolicyEligibility eligibility = baseEligibility().minimumAge(50).maximumAge(20).build();

        ConditionResult age = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.AGE);

        assertThat(age.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(age.reason()).isEqualTo("연령 조건 데이터가 올바르지 않음");
    }

    @Test
    void 정상적인_연령_조건에서_birthDate가_null이면_NEEDS_REVIEW다() {
        UserProfile userProfile = baseProfile().birthDate(null).build();
        PolicyEligibility eligibility = baseEligibility().minimumAge(20).build();

        ConditionResult age = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.AGE);

        assertThat(age.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(age.reason()).isEqualTo("생년월일 정보가 없어 자동 판정할 수 없음");
    }

    @Test
    void 소득_조건이_전부_null이면_MATCHED다() {
        UserProfile userProfile = baseProfile().build();
        PolicyEligibility eligibility = baseEligibility().build();

        ConditionResult income = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.INCOME);

        assertThat(income.status()).isEqualTo(ConditionStatus.MATCHED);
        assertThat(income.reason()).isEqualTo("소득 제한 없음");
    }

    @Test
    void 소득_범위값은_있는데_incomeType이_없으면_NEEDS_REVIEW다() {
        UserProfile userProfile = baseProfile().build();
        PolicyEligibility eligibility = baseEligibility().maximumIncomeValue(100).build();

        ConditionResult income = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.INCOME);

        assertThat(income.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(income.reason()).isEqualTo("소득 기준 유형이 없어 자동 판정할 수 없음");
    }

    @Test
    void incomeType은_있는데_최소_최대값이_둘다_없으면_NEEDS_REVIEW다() {
        UserProfile userProfile = baseProfile().build();
        PolicyEligibility eligibility = baseEligibility().incomeType(IncomeType.MEDIAN_PERCENTAGE).build();

        ConditionResult income = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.INCOME);

        assertThat(income.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(income.reason()).isEqualTo("소득 기준값이 없어 자동 판정할 수 없음");
    }

    @Test
    void incomeType이_다르면_NEEDS_REVIEW다() {
        UserProfile userProfile = baseProfile().incomeType(IncomeType.MEDIAN_PERCENTAGE).build();
        PolicyEligibility eligibility = baseEligibility().incomeType(IncomeType.ABSOLUTE).maximumIncomeValue(100).build();

        ConditionResult income = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.INCOME);

        assertThat(income.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(income.reason()).isEqualTo("소득 산정 기준이 달라 직접 비교할 수 없습니다.");
    }

    @Test
    void incomeType이_같고_범위_안이면_MATCHED_범위_밖이면_NOT_MATCHED다() {
        PolicyEligibility eligibility = baseEligibility()
                .incomeType(IncomeType.MEDIAN_PERCENTAGE).minimumIncomeValue(50).maximumIncomeValue(100).build();

        UserProfile within = baseProfile().incomeType(IncomeType.MEDIAN_PERCENTAGE).incomeValue(80).build();
        UserProfile atBoundary = baseProfile().incomeType(IncomeType.MEDIAN_PERCENTAGE).incomeValue(100).build();
        UserProfile outside = baseProfile().incomeType(IncomeType.MEDIAN_PERCENTAGE).incomeValue(101).build();

        assertThat(evaluateSingle(within, nationalPolicy(), eligibility, ConditionType.INCOME).status())
                .isEqualTo(ConditionStatus.MATCHED);
        assertThat(evaluateSingle(atBoundary, nationalPolicy(), eligibility, ConditionType.INCOME).status())
                .isEqualTo(ConditionStatus.MATCHED);
        assertThat(evaluateSingle(outside, nationalPolicy(), eligibility, ConditionType.INCOME).status())
                .isEqualTo(ConditionStatus.NOT_MATCHED);
    }

    @Test
    void 소득_최소값이_최대값보다_크면_NEEDS_REVIEW다() {
        UserProfile userProfile = baseProfile().incomeType(IncomeType.MEDIAN_PERCENTAGE).build();
        PolicyEligibility eligibility = baseEligibility()
                .incomeType(IncomeType.MEDIAN_PERCENTAGE).minimumIncomeValue(100).maximumIncomeValue(50).build();

        ConditionResult income = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.INCOME);

        assertThat(income.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(income.reason()).isEqualTo("소득 조건 데이터가 올바르지 않음");
    }

    @Test
    void 사용자_소득값이_null이면_NEEDS_REVIEW이고_NPE가_발생하지_않는다() {
        UserProfile userProfile = baseProfile().incomeType(IncomeType.MEDIAN_PERCENTAGE).incomeValue(null).build();
        PolicyEligibility eligibility = baseEligibility()
                .incomeType(IncomeType.MEDIAN_PERCENTAGE).maximumIncomeValue(100).build();

        ConditionResult income = evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.INCOME);

        assertThat(income.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(income.reason()).isEqualTo("사용자 소득 정보가 없어 자동 판정할 수 없음");
    }

    @Test
    void 고용상태가_null이거나_빈배열이면_MATCHED_포함되면_MATCHED_미포함이면_NOT_MATCHED다() {
        UserProfile userProfile = baseProfile().employmentStatus(EmploymentStatus.JOB_SEEKER).build();

        PolicyEligibility withNull = baseEligibility().build();
        PolicyEligibility withEmpty = baseEligibility().allowedEmploymentStatuses(List.of()).build();
        PolicyEligibility withMatch = baseEligibility().allowedEmploymentStatuses(List.of("JOB_SEEKER")).build();
        PolicyEligibility withoutMatch = baseEligibility().allowedEmploymentStatuses(List.of("EMPLOYED")).build();

        assertThat(evaluateSingle(userProfile, nationalPolicy(), withNull, ConditionType.EMPLOYMENT_STATUS).status())
                .isEqualTo(ConditionStatus.MATCHED);
        assertThat(evaluateSingle(userProfile, nationalPolicy(), withEmpty, ConditionType.EMPLOYMENT_STATUS).status())
                .isEqualTo(ConditionStatus.MATCHED);
        assertThat(evaluateSingle(userProfile, nationalPolicy(), withMatch, ConditionType.EMPLOYMENT_STATUS).status())
                .isEqualTo(ConditionStatus.MATCHED);
        assertThat(evaluateSingle(userProfile, nationalPolicy(), withoutMatch, ConditionType.EMPLOYMENT_STATUS).status())
                .isEqualTo(ConditionStatus.NOT_MATCHED);
    }

    @Test
    void 고용상태_제한이_있는데_사용자_고용상태가_null이면_NEEDS_REVIEW이고_NPE가_발생하지_않는다() {
        UserProfile userProfile = baseProfile().employmentStatus(null).build();
        PolicyEligibility eligibility = baseEligibility().allowedEmploymentStatuses(List.of("JOB_SEEKER")).build();

        ConditionResult employmentStatus =
                evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.EMPLOYMENT_STATUS);

        assertThat(employmentStatus.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(employmentStatus.reason()).isEqualTo("사용자 고용 상태 정보가 없어 자동 판정할 수 없음");
    }

    @Test
    void 고용상태_제한이_없으면_사용자_고용상태가_null이어도_MATCHED다() {
        UserProfile userProfile = baseProfile().employmentStatus(null).build();
        PolicyEligibility eligibility = baseEligibility().build();

        ConditionResult employmentStatus =
                evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.EMPLOYMENT_STATUS);

        assertThat(employmentStatus.status()).isEqualTo(ConditionStatus.MATCHED);
    }

    @Test
    void 가구유형이_null이거나_빈배열이면_MATCHED_포함되면_MATCHED_미포함이면_NOT_MATCHED다() {
        UserProfile userProfile = baseProfile().householdType(HouseholdType.SINGLE).build();

        PolicyEligibility withNull = baseEligibility().build();
        PolicyEligibility withEmpty = baseEligibility().allowedHouseholdTypes(List.of()).build();
        PolicyEligibility withMatch = baseEligibility().allowedHouseholdTypes(List.of("SINGLE")).build();
        PolicyEligibility withoutMatch = baseEligibility().allowedHouseholdTypes(List.of("ELDERLY")).build();

        assertThat(evaluateSingle(userProfile, nationalPolicy(), withNull, ConditionType.HOUSEHOLD_TYPE).status())
                .isEqualTo(ConditionStatus.MATCHED);
        assertThat(evaluateSingle(userProfile, nationalPolicy(), withEmpty, ConditionType.HOUSEHOLD_TYPE).status())
                .isEqualTo(ConditionStatus.MATCHED);
        assertThat(evaluateSingle(userProfile, nationalPolicy(), withMatch, ConditionType.HOUSEHOLD_TYPE).status())
                .isEqualTo(ConditionStatus.MATCHED);
        assertThat(evaluateSingle(userProfile, nationalPolicy(), withoutMatch, ConditionType.HOUSEHOLD_TYPE).status())
                .isEqualTo(ConditionStatus.NOT_MATCHED);
    }

    @Test
    void 가구유형_제한이_있는데_사용자_가구유형이_null이면_NEEDS_REVIEW이고_NPE가_발생하지_않는다() {
        UserProfile userProfile = baseProfile().householdType(null).build();
        PolicyEligibility eligibility = baseEligibility().allowedHouseholdTypes(List.of("SINGLE")).build();

        ConditionResult householdType =
                evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.HOUSEHOLD_TYPE);

        assertThat(householdType.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(householdType.reason()).isEqualTo("사용자 가구 유형 정보가 없어 자동 판정할 수 없음");
    }

    @Test
    void 가구유형_제한이_없으면_사용자_가구유형이_null이어도_MATCHED다() {
        UserProfile userProfile = baseProfile().householdType(null).build();
        PolicyEligibility eligibility = baseEligibility().build();

        ConditionResult householdType =
                evaluateSingle(userProfile, nationalPolicy(), eligibility, ConditionType.HOUSEHOLD_TYPE);

        assertThat(householdType.status()).isEqualTo(ConditionStatus.MATCHED);
    }

    @Test
    void 지역_고용상태_가구유형이_모두_null이고_전부_제한이_있어도_예외없이_NEEDS_REVIEW로_평가된다() {
        UserProfile userProfile = baseProfile().region(null).employmentStatus(null).householdType(null).build();
        PolicyEligibility eligibility = baseEligibility()
                .allowedEmploymentStatuses(List.of("JOB_SEEKER"))
                .allowedHouseholdTypes(List.of("SINGLE"))
                .build();

        PolicyEligibilityResult result =
                evaluator.evaluate(userProfile, regionalPolicy(), List.of(seoulRegion.getId()), eligibility);

        assertThat(conditionOf(result, ConditionType.REGION).status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(conditionOf(result, ConditionType.EMPLOYMENT_STATUS).status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(conditionOf(result, ConditionType.HOUSEHOLD_TYPE).status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(result.overallStatus()).isEqualTo(EligibilityStatus.NEEDS_REVIEW);
    }

    @Test
    void genderCondition이_있으면_NEEDS_REVIEW_조건이_추가되고_없으면_추가되지_않는다() {
        UserProfile userProfile = baseProfile().build();
        PolicyEligibility withGender = baseEligibility().genderCondition("FEMALE").build();
        PolicyEligibility withoutGender = baseEligibility().build();

        PolicyEligibilityResult withResult = evaluator.evaluate(userProfile, nationalPolicy(), List.of(), withGender);
        PolicyEligibilityResult withoutResult = evaluator.evaluate(userProfile, nationalPolicy(), List.of(), withoutGender);

        assertThat(withResult.conditionResults()).anyMatch(r -> r.type() == ConditionType.GENDER
                && r.status() == ConditionStatus.NEEDS_REVIEW);
        assertThat(withResult.overallStatus()).isEqualTo(EligibilityStatus.NEEDS_REVIEW);
        assertThat(withoutResult.conditionResults()).noneMatch(r -> r.type() == ConditionType.GENDER);
    }

    @Test
    void additionalConditions이_있으면_NEEDS_REVIEW_조건이_추가되고_없으면_추가되지_않는다() {
        UserProfile userProfile = baseProfile().build();
        PolicyEligibility withAdditional = baseEligibility()
                .additionalConditions(Map.of("note", "추가 서류 필요")).build();
        PolicyEligibility withoutAdditional = baseEligibility().build();

        PolicyEligibilityResult withResult = evaluator.evaluate(userProfile, nationalPolicy(), List.of(), withAdditional);
        PolicyEligibilityResult withoutResult = evaluator.evaluate(userProfile, nationalPolicy(), List.of(), withoutAdditional);

        assertThat(withResult.conditionResults()).anyMatch(r -> r.type() == ConditionType.ADDITIONAL_CONDITIONS
                && r.status() == ConditionStatus.NEEDS_REVIEW);
        assertThat(withoutResult.conditionResults()).noneMatch(r -> r.type() == ConditionType.ADDITIONAL_CONDITIONS);
    }

    @Test
    void additionalConditions이_빈_Map이면_조건이_추가되지_않는다() {
        UserProfile userProfile = baseProfile().build();
        PolicyEligibility eligibility = baseEligibility().additionalConditions(Map.of()).build();

        PolicyEligibilityResult result = evaluator.evaluate(userProfile, nationalPolicy(), List.of(), eligibility);

        assertThat(result.conditionResults()).noneMatch(r -> r.type() == ConditionType.ADDITIONAL_CONDITIONS);
    }

    @Test
    void NOT_MATCHED와_NEEDS_REVIEW가_섞이면_NOT_MATCHED가_우선해_INELIGIBLE이다() {
        UserProfile userProfile = baseProfile().incomeType(IncomeType.MEDIAN_PERCENTAGE).build();
        PolicyEligibility eligibility = baseEligibility()
                .minimumAge(99) // 나이 조건 NOT_MATCHED 유발
                .incomeType(IncomeType.ABSOLUTE).maximumIncomeValue(100) // 소득 incomeType 불일치로 NEEDS_REVIEW 유발
                .build();

        PolicyEligibilityResult result = evaluator.evaluate(userProfile, nationalPolicy(), List.of(), eligibility);

        assertThat(result.overallStatus()).isEqualTo(EligibilityStatus.INELIGIBLE);
        assertThat(result.overallReason()).isEqualTo("충족하지 못한 자격 조건이 있습니다.");
    }

    @Test
    void NEEDS_REVIEW만_있으면_전체_NEEDS_REVIEW다() {
        UserProfile userProfile = baseProfile().incomeType(IncomeType.MEDIAN_PERCENTAGE).build();
        PolicyEligibility eligibility = baseEligibility()
                .incomeType(IncomeType.ABSOLUTE).maximumIncomeValue(100) // NEEDS_REVIEW만 유발
                .build();

        PolicyEligibilityResult result = evaluator.evaluate(userProfile, nationalPolicy(), List.of(), eligibility);

        assertThat(result.overallStatus()).isEqualTo(EligibilityStatus.NEEDS_REVIEW);
        assertThat(result.overallReason()).isEqualTo("자동 판정할 수 없는 조건이 있습니다.");
    }

    private ConditionResult evaluateSingle(UserProfile userProfile, Policy policy, PolicyEligibility eligibility,
                                            ConditionType type) {
        PolicyEligibilityResult result = evaluator.evaluate(userProfile, policy, List.of(), eligibility);
        return conditionOf(result, type);
    }

    private ConditionResult conditionOf(PolicyEligibilityResult result, ConditionType type) {
        return result.conditionResults().stream()
                .filter(r -> r.type() == type)
                .findFirst()
                .orElseThrow();
    }

    private UserProfile.UserProfileBuilder baseProfile() {
        return UserProfile.builder()
                .birthDate(LocalDate.of(1998, 5, 14))
                .region(seoulRegion)
                .gender("F")
                .incomeType(IncomeType.MEDIAN_PERCENTAGE)
                .incomeValue(80)
                .employmentStatus(EmploymentStatus.JOB_SEEKER)
                .householdType(HouseholdType.SINGLE);
    }

    private PolicyEligibility.PolicyEligibilityBuilder baseEligibility() {
        return PolicyEligibility.builder();
    }

    private Policy nationalPolicy() {
        return Policy.builder()
                .title("전국 테스트 정책")
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.OPEN)
                .build();
    }

    private Policy regionalPolicy() {
        return Policy.builder()
                .title("지역 테스트 정책")
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(RegionScope.REGIONAL)
                .status(PolicyStatus.OPEN)
                .build();
    }

    private Region region(Long id, String code, String name) {
        Region region = Region.builder().code(code).name(name).build();
        ReflectionTestUtils.setField(region, "id", id);
        return region;
    }
}
