package com.mozip.server.policy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Category;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PolicyPackageSelectorTest {

    private static final PolicyAvailabilityResult AVAILABLE = new PolicyAvailabilityResult(
            PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.ALWAYS_OPEN, false);
    private static final PolicyAvailabilityResult NEEDS_REVIEW = new PolicyAvailabilityResult(
            PolicyAvailability.NEEDS_REVIEW, PolicyAvailabilityReason.UNKNOWN_APPLICATION_TYPE, false);
    private static final PolicyAvailabilityResult UPCOMING = new PolicyAvailabilityResult(
            PolicyAvailability.UNAVAILABLE, PolicyAvailabilityReason.BEFORE_APPLICATION_PERIOD, false);
    private static final PolicyAvailabilityResult ENDED = new PolicyAvailabilityResult(
            PolicyAvailability.UNAVAILABLE, PolicyAvailabilityReason.AFTER_APPLICATION_PERIOD, false);
    private static final PolicyAvailabilityResult CLOSED = new PolicyAvailabilityResult(
            PolicyAvailability.UNAVAILABLE, PolicyAvailabilityReason.CLOSED, false);

    private final Map<Long, PolicyEligibility> eligibilities = new HashMap<>();
    private final Map<Long, List<Category>> categories = new HashMap<>();

    @Test
    void 나이_제한이_패키지_나이와_비슷하면_대상_표현이_없어도_포함된다() {
        Candidate candidate = candidate(1L, "일자리 지원금", AVAILABLE, "EMPLOYMENT");
        ageLimit(candidate, 20, 30);

        Map<PolicyPackageSection, List<Candidate>> grouped = select(PolicyPackage.JOB_SEEKER, candidate);

        assertThat(idsOf(grouped, PolicyPackage.JOB_SEEKER, "employment")).containsExactly(1L);
    }

    @Test
    void 나이_제한이_넓으면_대상_표현이_있을_때만_포함된다() {
        Candidate broadGeneral = candidate(1L, "어업인 안전 교육", AVAILABLE, "EDUCATION");
        ageLimit(broadGeneral, 19, null);
        Candidate broadTargeted = candidate(2L, "청년 교육 지원", AVAILABLE, "EDUCATION");
        ageLimit(broadTargeted, 18, 65);

        Map<PolicyPackageSection, List<Candidate>> grouped =
                select(PolicyPackage.JOB_SEEKER, broadGeneral, broadTargeted);

        assertThat(idsOf(grouped, PolicyPackage.JOB_SEEKER, "education")).containsExactly(2L);
    }

    @Test
    void 패키지_범위에서_5세_이내로_벗어난_나이_제한은_비슷한_범위로_본다() {
        Candidate withinSlack = candidate(1L, "일자리 지원", AVAILABLE, "EMPLOYMENT");
        ageLimit(withinSlack, 15, 44);
        Candidate beyondSlack = candidate(2L, "일자리 지원", AVAILABLE, "EMPLOYMENT");
        ageLimit(beyondSlack, 15, 45);

        Map<PolicyPackageSection, List<Candidate>> grouped =
                select(PolicyPackage.JOB_SEEKER, withinSlack, beyondSlack);

        assertThat(idsOf(grouped, PolicyPackage.JOB_SEEKER, "employment")).containsExactly(1L);
    }

    @Test
    void 상한이_없는_패키지는_하한만_비교한다() {
        Candidate senior = candidate(1L, "보건 서비스", AVAILABLE, "WELFARE");
        ageLimit(senior, 60, null);
        Candidate adult = candidate(2L, "보건 서비스", AVAILABLE, "WELFARE");
        ageLimit(adult, 19, null);

        Map<PolicyPackageSection, List<Candidate>> grouped = select(PolicyPackage.SENIOR, senior, adult);

        assertThat(idsOf(grouped, PolicyPackage.SENIOR, "welfare")).containsExactly(1L);
    }

    @Test
    void 나이_제한이_패키지_나이와_겹치지_않으면_대상_표현이_있어도_제외된다() {
        Candidate candidate = candidate(1L, "청년 일자리 지원", AVAILABLE, "EMPLOYMENT");
        ageLimit(candidate, 65, null);

        assertThat(countOf(PolicyPackage.JOB_SEEKER, candidate)).isZero();
    }

    @Test
    void 나이_제한이_없으면_대상_표현이_있는_정책만_포함된다() {
        Candidate targeted = candidate(1L, "청년 일자리 지원", AVAILABLE, "EMPLOYMENT");
        Candidate general = candidate(2L, "일자리 지원", AVAILABLE, "EMPLOYMENT");

        Map<PolicyPackageSection, List<Candidate>> grouped = select(PolicyPackage.JOB_SEEKER, targeted, general);

        assertThat(idsOf(grouped, PolicyPackage.JOB_SEEKER, "employment")).containsExactly(1L);
    }

    @Test
    void 대상_표현은_지원대상_본문에서도_찾는다() {
        Candidate candidate = candidate(1L, "교통비 지원", AVAILABLE, "WELFARE");
        ReflectionTestUtils.setField(candidate.policy(), "targetDescription", "만 65세 이상 어르신");

        Map<PolicyPackageSection, List<Candidate>> grouped = select(PolicyPackage.SENIOR, candidate);

        assertThat(idsOf(grouped, PolicyPackage.SENIOR, "transport")).containsExactly(1L);
    }

    @Test
    void 청소년_패키지는_대학생을_학생으로_보지_않는다() {
        Candidate universityStudent = candidate(1L, "대학생 학자금 지원", AVAILABLE, "EDUCATION");
        Candidate highSchoolStudent = candidate(2L, "고등학생 교육비 지원", AVAILABLE, "EDUCATION");

        Map<PolicyPackageSection, List<Candidate>> grouped =
                select(PolicyPackage.TEEN, universityStudent, highSchoolStudent);

        assertThat(idsOf(grouped, PolicyPackage.TEEN, "education")).containsExactly(2L);
    }

    @Test
    void 키워드_섹션이_카테고리_섹션보다_먼저_배정되고_한_섹션에만_들어간다() {
        Candidate candidate = candidate(1L, "어르신 버스 요금 지원", AVAILABLE, "WELFARE");

        Map<PolicyPackageSection, List<Candidate>> grouped = select(PolicyPackage.SENIOR, candidate);

        assertThat(idsOf(grouped, PolicyPackage.SENIOR, "transport")).containsExactly(1L);
        assertThat(idsOf(grouped, PolicyPackage.SENIOR, "welfare")).isEmpty();
    }

    @Test
    void 월세_키워드가_있는_주거_정책은_월세전세_섹션에_없으면_주거_섹션에_들어간다() {
        Candidate rent = candidate(1L, "청년 월세 지원", AVAILABLE, "HOUSING");
        Candidate housing = candidate(2L, "청년 주택 공급", AVAILABLE, "HOUSING");

        Map<PolicyPackageSection, List<Candidate>> grouped = select(PolicyPackage.SOLO_YOUTH, rent, housing);

        assertThat(idsOf(grouped, PolicyPackage.SOLO_YOUTH, "rent")).containsExactly(1L);
        assertThat(idsOf(grouped, PolicyPackage.SOLO_YOUTH, "housing")).containsExactly(2L);
    }

    @Test
    void 월세_키워드가_있어도_주거_카테고리가_아니면_월세전세_섹션에_들어가지_않는다() {
        Candidate candidate = candidate(1L, "청년 월세 지원", AVAILABLE, "WELFARE");

        assertThat(countOf(PolicyPackage.SOLO_YOUTH, candidate)).isZero();
    }

    @Test
    void 취업과_창업이_모두_붙은_정책은_두_섹션에_모두_들어가고_정책_수는_한_번만_센다() {
        Candidate candidate = candidate(1L, "청년 창업 일자리 지원", AVAILABLE, "EMPLOYMENT", "STARTUP");

        Map<PolicyPackageSection, List<Candidate>> grouped = select(PolicyPackage.JOB_SEEKER, candidate);

        assertThat(idsOf(grouped, PolicyPackage.JOB_SEEKER, "employment")).containsExactly(1L);
        assertThat(idsOf(grouped, PolicyPackage.JOB_SEEKER, "startup")).containsExactly(1L);
        assertThat(PolicyPackageSelector.countPolicies(grouped, Candidate::policy)).isEqualTo(1);
    }

    @Test
    void 노후_섹션은_취업_카테고리이거나_연금_노후_퇴직_키워드가_있으면_포함된다() {
        Candidate job = candidate(1L, "어르신 일자리 사업", AVAILABLE, "EMPLOYMENT");
        Candidate pension = candidate(2L, "어르신 연금 안내", AVAILABLE, "WELFARE");

        Map<PolicyPackageSection, List<Candidate>> grouped = select(PolicyPackage.SENIOR, job, pension);

        assertThat(idsOf(grouped, PolicyPackage.SENIOR, "retirement")).containsExactly(1L, 2L);
        assertThat(idsOf(grouped, PolicyPackage.SENIOR, "welfare")).isEmpty();
    }

    @Test
    void 마감된_정책은_제외하고_신청기간_확인_필요와_예정_정책은_포함한다() {
        Candidate available = candidate(1L, "청년 취업 지원 A", AVAILABLE, "EMPLOYMENT");
        Candidate needsReview = candidate(2L, "청년 취업 지원 B", NEEDS_REVIEW, "EMPLOYMENT");
        Candidate upcoming = candidate(3L, "청년 취업 지원 C", UPCOMING, "EMPLOYMENT");
        Candidate ended = candidate(4L, "청년 취업 지원 D", ENDED, "EMPLOYMENT");
        Candidate closed = candidate(5L, "청년 취업 지원 E", CLOSED, "EMPLOYMENT");

        Map<PolicyPackageSection, List<Candidate>> grouped =
                select(PolicyPackage.JOB_SEEKER, available, needsReview, upcoming, ended, closed);

        assertThat(idsOf(grouped, PolicyPackage.JOB_SEEKER, "employment")).containsExactly(1L, 2L, 3L);
    }

    @Test
    void 어느_섹션에도_맞지_않는_정책은_제외된다() {
        Candidate candidate = candidate(1L, "청년 문화 행사", AVAILABLE, "CULTURE");

        assertThat(countOf(PolicyPackage.JOB_SEEKER, candidate)).isZero();
    }

    @Test
    void 결과는_섹션_표시_순서대로_빈_섹션도_포함한다() {
        Map<PolicyPackageSection, List<Candidate>> grouped = select(PolicyPackage.SENIOR);

        assertThat(grouped.keySet()).extracting(PolicyPackageSection::key)
                .containsExactly("welfare", "transport", "culture", "retirement");
        assertThat(grouped.values()).allMatch(List::isEmpty);
    }

    @Test
    void 공개_정렬은_접수중_기간확인필요_예정_순이고_같은_상태면_나이를_명시한_정책이_먼저다() {
        Candidate upcoming = candidate(1L, "청년 취업 지원 A", UPCOMING, "EMPLOYMENT");
        Candidate needsReview = candidate(2L, "청년 취업 지원 B", NEEDS_REVIEW, "EMPLOYMENT");
        Candidate availableNoAge = candidate(3L, "청년 취업 지원 C", AVAILABLE, "EMPLOYMENT");
        Candidate availableWithAge = candidate(4L, "청년 취업 지원 D", AVAILABLE, "EMPLOYMENT");
        ageLimit(availableWithAge, 19, 34);

        List<Long> sortedIds = List.of(upcoming, needsReview, availableNoAge, availableWithAge).stream()
                .sorted(PolicyPackageSelector.publicOrder(PolicyPackage.JOB_SEEKER, Candidate::policy,
                        Candidate::availability, eligibilities))
                .map(candidate -> candidate.policy().getId())
                .toList();

        assertThat(sortedIds).containsExactly(4L, 3L, 2L, 1L);
    }

    @Test
    void 패키지_id와_섹션_key로_조회한다() {
        assertThat(PolicyPackage.fromId("job-seeker")).contains(PolicyPackage.JOB_SEEKER);
        assertThat(PolicyPackage.fromId("unknown")).isEmpty();
        assertThat(PolicyPackage.TEEN.findSection("transport")).isPresent();
        assertThat(PolicyPackage.TEEN.findSection("housing")).isEmpty();
    }

    private record Candidate(Policy policy, PolicyAvailabilityResult availability) {
    }

    private Candidate candidate(Long id, String title, PolicyAvailabilityResult availability, String... categoryCodes) {
        Policy policy = Policy.builder()
                .title(title)
                .applicationType(ApplicationType.ALWAYS)
                .applicationEndDate(LocalDate.of(2026, 12, 31))
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        categories.put(id, java.util.Arrays.stream(categoryCodes)
                .map(code -> Category.builder().code(code).name(code).build())
                .toList());
        return new Candidate(policy, availability);
    }

    private void ageLimit(Candidate candidate, Integer minimumAge, Integer maximumAge) {
        eligibilities.put(candidate.policy().getId(), PolicyEligibility.builder()
                .policy(candidate.policy())
                .minimumAge(minimumAge)
                .maximumAge(maximumAge)
                .build());
    }

    private Map<PolicyPackageSection, List<Candidate>> select(PolicyPackage policyPackage, Candidate... candidates) {
        return PolicyPackageSelector.select(policyPackage, List.of(candidates), Candidate::policy,
                Candidate::availability, eligibilities, categories);
    }

    private int countOf(PolicyPackage policyPackage, Candidate... candidates) {
        return PolicyPackageSelector.countPolicies(select(policyPackage, candidates), Candidate::policy);
    }

    private List<Long> idsOf(Map<PolicyPackageSection, List<Candidate>> grouped, PolicyPackage policyPackage,
                             String sectionKey) {
        return grouped.get(policyPackage.findSection(sectionKey).orElseThrow()).stream()
                .map(candidate -> candidate.policy().getId())
                .toList();
    }
}
