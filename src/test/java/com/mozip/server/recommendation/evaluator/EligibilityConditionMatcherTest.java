package com.mozip.server.recommendation.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.recommendation.domain.ConditionStatus;
import com.mozip.server.region.entity.Region;
import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EligibilityConditionMatcherTest {

    private final EligibilityConditionMatcher matcher = new EligibilityConditionMatcher();

    @Test
    void 나이_제한이_없으면_age가_null이어도_MATCHED다() {
        assertThat(matcher.matchAge(null, null, null).status()).isEqualTo(ConditionStatus.MATCHED);
    }

    @Test
    void 최소연령이_최대연령보다_크면_age와_무관하게_NEEDS_REVIEW다() {
        assertThat(matcher.matchAge(30, 50, 20).status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
    }

    @Test
    void 나이_제한이_있는데_age가_null이면_NEEDS_REVIEW다() {
        var result = matcher.matchAge(null, 20, 40);
        assertThat(result.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(result.reason()).isEqualTo("나이 정보가 없어 자동 판정할 수 없음");
    }

    @Test
    void 나이가_범위_안이면_MATCHED_밖이면_NOT_MATCHED다() {
        assertThat(matcher.matchAge(28, 20, 40).status()).isEqualTo(ConditionStatus.MATCHED);
        assertThat(matcher.matchAge(15, 20, 40).status()).isEqualTo(ConditionStatus.NOT_MATCHED);
    }

    @Test
    void 전국_정책이면_지역과_무관하게_MATCHED다() {
        var result = matcher.matchRegion(RegionScope.NATIONAL, List.of(), null);
        assertThat(result.status()).isEqualTo(ConditionStatus.MATCHED);
    }

    @Test
    void 지역_정책인데_사용자_지역이_null이면_NEEDS_REVIEW이고_NPE가_발생하지_않는다() {
        var result = matcher.matchRegion(RegionScope.REGIONAL, List.of(1L), null);
        assertThat(result.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
        assertThat(result.reason()).isEqualTo("사용자 지역 정보가 없어 자동 판정할 수 없음");
    }

    @Test
    void 사용자_지역의_상위_지역이_정책_지역과_같으면_MATCHED다() {
        Region seoul = region(1L, "SEOUL", null);
        Region mapo = region(2L, "SEOUL_MAPO", seoul);

        var result = matcher.matchRegion(RegionScope.REGIONAL, List.of(seoul.getId()), mapo);

        assertThat(result.status()).isEqualTo(ConditionStatus.MATCHED);
    }

    @Test
    void 소득_조건이_전부_null이면_MATCHED다() {
        var result = matcher.matchIncome(null, null, null, null, null);
        assertThat(result.status()).isEqualTo(ConditionStatus.MATCHED);
    }

    @Test
    void 소득_유형이_다르면_NEEDS_REVIEW다() {
        var result = matcher.matchIncome(IncomeType.ABSOLUTE, 100, null, IncomeType.MEDIAN_PERCENTAGE, 50);
        assertThat(result.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
    }

    @Test
    void 소득이_범위_안이면_MATCHED_밖이면_NOT_MATCHED다() {
        var matched = matcher.matchIncome(IncomeType.MEDIAN_PERCENTAGE, 50, 100, IncomeType.MEDIAN_PERCENTAGE, 80);
        var notMatched = matcher.matchIncome(IncomeType.MEDIAN_PERCENTAGE, 50, 100, IncomeType.MEDIAN_PERCENTAGE, 101);

        assertThat(matched.status()).isEqualTo(ConditionStatus.MATCHED);
        assertThat(notMatched.status()).isEqualTo(ConditionStatus.NOT_MATCHED);
    }

    @Test
    void 고용상태_제한이_없으면_MATCHED_포함되면_MATCHED_미포함이면_NOT_MATCHED다() {
        assertThat(matcher.matchEmploymentStatus(null, null).status()).isEqualTo(ConditionStatus.MATCHED);
        assertThat(matcher.matchEmploymentStatus(List.of("JOB_SEEKER"), EmploymentStatus.JOB_SEEKER).status())
                .isEqualTo(ConditionStatus.MATCHED);
        assertThat(matcher.matchEmploymentStatus(List.of("EMPLOYED"), EmploymentStatus.JOB_SEEKER).status())
                .isEqualTo(ConditionStatus.NOT_MATCHED);
    }

    @Test
    void 고용상태_제한이_있는데_값이_null이면_NEEDS_REVIEW다() {
        var result = matcher.matchEmploymentStatus(List.of("JOB_SEEKER"), null);
        assertThat(result.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
    }

    @Test
    void 가구유형_제한이_없으면_MATCHED_포함되면_MATCHED_미포함이면_NOT_MATCHED다() {
        assertThat(matcher.matchHouseholdType(null, null).status()).isEqualTo(ConditionStatus.MATCHED);
        assertThat(matcher.matchHouseholdType(List.of("SINGLE"), HouseholdType.SINGLE).status())
                .isEqualTo(ConditionStatus.MATCHED);
        assertThat(matcher.matchHouseholdType(List.of("ELDERLY"), HouseholdType.SINGLE).status())
                .isEqualTo(ConditionStatus.NOT_MATCHED);
    }

    @Test
    void 가구유형_제한이_있는데_값이_null이면_NEEDS_REVIEW다() {
        var result = matcher.matchHouseholdType(List.of("SINGLE"), null);
        assertThat(result.status()).isEqualTo(ConditionStatus.NEEDS_REVIEW);
    }

    private Region region(Long id, String code, Region parent) {
        Region region = Region.builder().code(code).name(code).parent(parent).build();
        ReflectionTestUtils.setField(region, "id", id);
        return region;
    }
}
