package com.mozip.server.ai.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.ai.dto.SemanticMatchPolicyRequest;
import com.mozip.server.ai.dto.SemanticMatchUserRequest;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyRegion;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.region.entity.Region;
import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.Gender;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;
import com.mozip.server.user.entity.UserProfile;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SemanticMatchRequestMapperTest {

    @Test
    void User의_모든_값이_채워져_있으면_그대로_매핑한다() {
        Region region = region(1L, "SEOUL_MAPO");
        UserProfile userProfile = UserProfile.builder()
                .birthDate(LocalDate.of(1998, 5, 14))
                .region(region)
                .gender(Gender.MALE)
                .incomeType(IncomeType.MEDIAN_PERCENTAGE)
                .incomeValue(3_000_000)
                .employmentStatus(EmploymentStatus.JOB_SEEKER)
                .householdType(HouseholdType.SINGLE)
                .build();

        SemanticMatchUserRequest request = SemanticMatchRequestMapper.toUserRequest(userProfile);

        assertThat(request.gender()).isEqualTo(Gender.MALE);
        assertThat(request.birthDate()).isEqualTo(LocalDate.of(1998, 5, 14));
        assertThat(request.regionCode()).isEqualTo("SEOUL_MAPO");
        assertThat(request.employmentStatus()).isEqualTo(EmploymentStatus.JOB_SEEKER);
        assertThat(request.householdType()).isEqualTo(HouseholdType.SINGLE);
        assertThat(request.incomeType()).isEqualTo(IncomeType.MEDIAN_PERCENTAGE);
    }

    @Test
    void User의_region이_없으면_regionCode는_null이다() {
        UserProfile userProfile = UserProfile.builder()
                .birthDate(LocalDate.of(1998, 5, 14))
                .region(null)
                .gender(Gender.FEMALE)
                .build();

        SemanticMatchUserRequest request = SemanticMatchRequestMapper.toUserRequest(userProfile);

        assertThat(request.regionCode()).isNull();
    }

    @Test
    void User의_선택_항목이_비어있으면_null로_매핑한다() {
        UserProfile userProfile = UserProfile.builder()
                .birthDate(null)
                .region(null)
                .gender(null)
                .incomeType(null)
                .employmentStatus(null)
                .householdType(null)
                .build();

        SemanticMatchUserRequest request = SemanticMatchRequestMapper.toUserRequest(userProfile);

        assertThat(request.gender()).isNull();
        assertThat(request.birthDate()).isNull();
        assertThat(request.regionCode()).isNull();
        assertThat(request.employmentStatus()).isNull();
        assertThat(request.householdType()).isNull();
        assertThat(request.incomeType()).isNull();
    }

    @Test
    void SemanticMatchUserRequest에는_incomeValue가_포함되지_않는다() {
        List<String> componentNames = List.of(SemanticMatchUserRequest.class.getRecordComponents()).stream()
                .map(RecordComponent::getName)
                .toList();

        assertThat(componentNames).doesNotContain("incomeValue");
    }

    @Test
    void PolicyEligibility가_존재하면_조건값을_그대로_매핑한다() {
        Policy policy = policy(101L, RegionScope.NATIONAL);
        PolicyEligibility eligibility = PolicyEligibility.builder()
                .policy(policy)
                .minimumAge(19)
                .maximumAge(34)
                .genderCondition(Gender.MALE)
                .incomeType(IncomeType.MEDIAN_PERCENTAGE)
                .allowedEmploymentStatuses(List.of("JOB_SEEKER"))
                .allowedHouseholdTypes(List.of("SINGLE", "ELDERLY"))
                .build();

        SemanticMatchPolicyRequest request = SemanticMatchRequestMapper.toPolicyRequest(
                policy, Optional.of(eligibility), List.of());

        assertThat(request.policyId()).isEqualTo(101L);
        assertThat(request.minimumAge()).isEqualTo(19);
        assertThat(request.maximumAge()).isEqualTo(34);
        assertThat(request.genderCondition()).isEqualTo(Gender.MALE);
        assertThat(request.incomeType()).isEqualTo(IncomeType.MEDIAN_PERCENTAGE);
        assertThat(request.allowedEmploymentStatuses()).containsExactly("JOB_SEEKER");
        assertThat(request.allowedHouseholdTypes()).containsExactly("SINGLE", "ELDERLY");
    }

    @Test
    void PolicyEligibility가_없으면_조건_필드는_모두_null_또는_빈리스트다() {
        Policy policy = policy(101L, RegionScope.NATIONAL);

        SemanticMatchPolicyRequest request = SemanticMatchRequestMapper.toPolicyRequest(
                policy, Optional.empty(), List.of());

        assertThat(request.minimumAge()).isNull();
        assertThat(request.maximumAge()).isNull();
        assertThat(request.genderCondition()).isNull();
        assertThat(request.incomeType()).isNull();
        assertThat(request.allowedEmploymentStatuses()).isEmpty();
        assertThat(request.allowedHouseholdTypes()).isEmpty();
    }

    @Test
    void PolicyEligibility의_scalar_필드가_null이면_그대로_null로_매핑한다() {
        Policy policy = policy(101L, RegionScope.NATIONAL);
        PolicyEligibility eligibility = PolicyEligibility.builder()
                .policy(policy)
                .minimumAge(null)
                .maximumAge(null)
                .genderCondition(null)
                .incomeType(null)
                .allowedEmploymentStatuses(List.of("JOB_SEEKER"))
                .allowedHouseholdTypes(List.of())
                .build();

        SemanticMatchPolicyRequest request = SemanticMatchRequestMapper.toPolicyRequest(
                policy, Optional.of(eligibility), List.of());

        assertThat(request.minimumAge()).isNull();
        assertThat(request.maximumAge()).isNull();
        assertThat(request.genderCondition()).isNull();
        assertThat(request.incomeType()).isNull();
    }

    @Test
    void PolicyEligibility의_컬렉션_필드가_null이면_빈리스트로_정규화한다() {
        Policy policy = policy(101L, RegionScope.NATIONAL);
        PolicyEligibility eligibility = PolicyEligibility.builder()
                .policy(policy)
                .allowedEmploymentStatuses(null)
                .allowedHouseholdTypes(null)
                .build();

        SemanticMatchPolicyRequest request = SemanticMatchRequestMapper.toPolicyRequest(
                policy, Optional.of(eligibility), List.of());

        assertThat(request.allowedEmploymentStatuses()).isEmpty();
        assertThat(request.allowedHouseholdTypes()).isEmpty();
    }

    @Test
    void NATIONAL_정책은_PolicyRegion이_있어도_regionCodes가_항상_빈리스트다() {
        Policy policy = policy(101L, RegionScope.NATIONAL);
        Region region = region(1L, "SEOUL_MAPO");
        List<PolicyRegion> policyRegions = List.of(PolicyRegion.builder().policy(policy).region(region).build());

        SemanticMatchPolicyRequest request = SemanticMatchRequestMapper.toPolicyRequest(
                policy, Optional.empty(), policyRegions);

        assertThat(request.regionScope()).isEqualTo(RegionScope.NATIONAL);
        assertThat(request.regionCodes()).isEmpty();
    }

    @Test
    void REGIONAL_정책은_여러_PolicyRegion의_코드를_모두_담는다() {
        Policy policy = policy(101L, RegionScope.REGIONAL);
        Region mapo = region(1L, "SEOUL_MAPO");
        Region jongno = region(2L, "SEOUL_JONGNO");
        List<PolicyRegion> policyRegions = List.of(
                PolicyRegion.builder().policy(policy).region(mapo).build(),
                PolicyRegion.builder().policy(policy).region(jongno).build()
        );

        SemanticMatchPolicyRequest request = SemanticMatchRequestMapper.toPolicyRequest(
                policy, Optional.empty(), policyRegions);

        assertThat(request.regionScope()).isEqualTo(RegionScope.REGIONAL);
        assertThat(request.regionCodes()).containsExactly("SEOUL_MAPO", "SEOUL_JONGNO");
    }

    @Test
    void REGIONAL_정책인데_PolicyRegion이_없으면_regionCodes는_빈리스트다() {
        Policy policy = policy(101L, RegionScope.REGIONAL);

        SemanticMatchPolicyRequest request = SemanticMatchRequestMapper.toPolicyRequest(
                policy, Optional.empty(), List.of());

        assertThat(request.regionScope()).isEqualTo(RegionScope.REGIONAL);
        assertThat(request.regionCodes()).isEmpty();
    }

    private Policy policy(Long id, RegionScope regionScope) {
        Organization organization = Organization.builder().name("서울시").build();
        Policy policy = Policy.builder()
                .organization(organization)
                .title("청년 지원 정책")
                .applicationType(com.mozip.server.policy.entity.ApplicationType.ALWAYS)
                .regionScope(regionScope)
                .status(com.mozip.server.policy.entity.PolicyStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }

    private Region region(Long id, String code) {
        Region region = Region.builder().code(code).name(code).build();
        ReflectionTestUtils.setField(region, "id", id);
        return region;
    }
}
