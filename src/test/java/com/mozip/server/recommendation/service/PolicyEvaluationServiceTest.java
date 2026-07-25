package com.mozip.server.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.dto.PolicyEvaluationResponse;
import com.mozip.server.region.entity.Region;
import com.mozip.server.region.repository.RegionRepository;
import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;
import com.mozip.server.user.entity.OAuthProvider;
import com.mozip.server.user.entity.User;
import com.mozip.server.user.entity.UserProfile;
import com.mozip.server.user.exception.UserProfileNotFoundException;
import com.mozip.server.user.repository.UserProfileRepository;
import com.mozip.server.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class PolicyEvaluationServiceTest {

    @Autowired
    private PolicyEvaluationService policyEvaluationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyEligibilityRepository policyEligibilityRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 적격성과_신청가능여부를_정상적으로_조합한다() {
        User user = createUser("combo@example.com", "combo-1");
        createProfile(user, seoulRegionOrCreate());
        Policy policy = createPolicy(RegionScope.NATIONAL, PolicyStatus.OPEN, ApplicationType.ALWAYS, null, null);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(policy).build());

        PolicyEvaluationResponse response = policyEvaluationService.evaluate(user.getId(), policy.getId());

        assertThat(response.policyId()).isEqualTo(policy.getId());
        assertThat(response.eligibility().status()).isEqualTo(EligibilityStatus.ELIGIBLE);
        assertThat(response.availability().status()).isEqualTo(PolicyAvailability.AVAILABLE);
        assertThat(response.availability().reason()).isEqualTo(PolicyAvailabilityReason.ALWAYS_APPLICATION_TYPE);
    }

    @Test
    void 신청_기간이_종료된_정책은_availability가_UNAVAILABLE이다() {
        User user = createUser("expired@example.com", "expired-1");
        createProfile(user, seoulRegionOrCreate());
        Policy policy = createPolicy(RegionScope.NATIONAL, PolicyStatus.OPEN, ApplicationType.PERIOD,
                LocalDate.now().minusYears(1), LocalDate.now().minusMonths(1));
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(policy).build());

        PolicyEvaluationResponse response = policyEvaluationService.evaluate(user.getId(), policy.getId());

        assertThat(response.availability().status()).isEqualTo(PolicyAvailability.UNAVAILABLE);
        assertThat(response.availability().reason()).isEqualTo(PolicyAvailabilityReason.AFTER_APPLICATION_PERIOD);
    }

    @Test
    void Eligibility_데이터가_없어도_availability는_독립적으로_판정된다() {
        User user = createUser("no-eligibility@example.com", "no-eligibility-1");
        createProfile(user, seoulRegionOrCreate());
        Policy policy = createPolicy(RegionScope.NATIONAL, PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);

        PolicyEvaluationResponse response = policyEvaluationService.evaluate(user.getId(), policy.getId());

        assertThat(response.eligibility().status()).isEqualTo(EligibilityStatus.NEEDS_REVIEW);
        assertThat(response.eligibility().overallReason()).isEqualTo("자격 조건 정보가 등록되지 않음");
        assertThat(response.eligibility().conditionResults()).isEmpty();
        assertThat(response.availability().status()).isEqualTo(PolicyAvailability.AVAILABLE);
        assertThat(response.availability().reason()).isEqualTo(PolicyAvailabilityReason.ALWAYS_OPEN);
    }

    @Test
    void 존재하지_않는_정책이면_예외가_발생한다() {
        User user = createUser("no-policy@example.com", "no-policy-1");
        createProfile(user, seoulRegionOrCreate());

        assertThatThrownBy(() -> policyEvaluationService.evaluate(user.getId(), 999999L))
                .isInstanceOf(PolicyNotFoundException.class);
    }

    @Test
    void 사용자_프로필이_없으면_예외가_발생한다() {
        User user = createUser("no-profile@example.com", "no-profile-1");
        Policy policy = createPolicy(RegionScope.NATIONAL, PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);

        assertThatThrownBy(() -> policyEvaluationService.evaluate(user.getId(), policy.getId()))
                .isInstanceOf(UserProfileNotFoundException.class);
    }

    private User createUser(String email, String providerUserId) {
        return userRepository.save(User.builder()
                .email(email)
                .provider(OAuthProvider.KAKAO)
                .providerUserId(providerUserId)
                .build());
    }

    private UserProfile createProfile(User user, Region region) {
        return userProfileRepository.save(UserProfile.builder()
                .user(user)
                .birthDate(LocalDate.of(1998, 5, 14))
                .region(region)
                .gender("F")
                .incomeType(IncomeType.MEDIAN_PERCENTAGE)
                .incomeValue(80)
                .employmentStatus(EmploymentStatus.JOB_SEEKER)
                .householdType(HouseholdType.SINGLE)
                .build());
    }

    private Region seoulRegionOrCreate() {
        return regionRepository.findAll().stream().findFirst()
                .orElseGet(() -> regionRepository.save(Region.builder().code("TEST_REGION").name("테스트지역").build()));
    }

    private Policy createPolicy(RegionScope regionScope, PolicyStatus status, ApplicationType applicationType,
                                 LocalDate startDate, LocalDate endDate) {
        Organization organization = Organization.builder()
                .name("테스트기관")
                .type("중앙부처")
                .build();
        entityManager.persist(organization);

        Policy policy = Policy.builder()
                .organization(organization)
                .title("테스트 정책")
                .applicationType(applicationType)
                .applicationStartDate(startDate)
                .applicationEndDate(endDate)
                .regionScope(regionScope)
                .status(status)
                .build();
        return policyRepository.save(policy);
    }
}
