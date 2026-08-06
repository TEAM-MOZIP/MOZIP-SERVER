package com.mozip.server.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyRegion;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRegionRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import com.mozip.server.recommendation.evaluator.PolicyEligibilityEvaluator;
import com.mozip.server.region.entity.Region;
import com.mozip.server.region.repository.RegionRepository;
import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.Gender;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class PolicyEligibilityServiceTest {

    @Autowired
    private PolicyEligibilityService policyEligibilityService;

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

    @MockitoSpyBean
    private PolicyRegionRepository policyRegionRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private PolicyEligibilityEvaluator policyEligibilityEvaluator;

    @Test
    void 프로필이_없으면_예외가_발생하고_평가자는_호출되지_않는다() {
        User user = createUser("no-profile@example.com", "no-profile-1");
        Policy policy = createPolicy(RegionScope.NATIONAL);

        assertThatThrownBy(() -> policyEligibilityService.evaluate(user.getId(), policy.getId()))
                .isInstanceOf(UserProfileNotFoundException.class);
    }

    @Test
    void 정책이_없으면_예외가_발생한다() {
        User user = createUser("no-policy@example.com", "no-policy-1");
        createProfile(user, seoulRegionOrCreate());

        assertThatThrownBy(() -> policyEligibilityService.evaluate(user.getId(), 999999L))
                .isInstanceOf(PolicyNotFoundException.class);
    }

    @Test
    void Eligibility가_없는_정책은_평가자에게_null로_전달된다() {
        User user = createUser("no-eligibility@example.com", "no-eligibility-1");
        UserProfile profile = createProfile(user, seoulRegionOrCreate());
        Policy policy = createPolicy(RegionScope.NATIONAL);
        PolicyEligibilityResult mockResult = new PolicyEligibilityResult(
                EligibilityStatus.NEEDS_REVIEW, "자격 조건 정보가 등록되지 않음", List.of());
        when(policyEligibilityEvaluator.evaluate(eq(profile), eq(policy), any(), isNull()))
                .thenReturn(mockResult);

        PolicyEligibilityResult result = policyEligibilityService.evaluate(user.getId(), policy.getId());

        assertThat(result.overallStatus()).isEqualTo(EligibilityStatus.NEEDS_REVIEW);
        verify(policyEligibilityEvaluator).evaluate(eq(profile), eq(policy), any(), isNull());
        verify(policyRegionRepository, never()).findRegionIdsByPolicyId(any());
    }

    @Test
    void NATIONAL_정책은_Eligibility가_있어도_지역_레포지토리를_호출하지_않는다() {
        User user = createUser("national-no-region-call@example.com", "national-no-region-call-1");
        createProfile(user, seoulRegionOrCreate());
        Policy policy = createPolicy(RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(policy).build());
        when(policyEligibilityEvaluator.evaluate(any(), any(), any(), any()))
                .thenReturn(new PolicyEligibilityResult(EligibilityStatus.ELIGIBLE, "테스트", List.of()));

        policyEligibilityService.evaluate(user.getId(), policy.getId());

        verify(policyRegionRepository, never()).findRegionIdsByPolicyId(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void 정책의_지역_목록이_평가자에게_그대로_전달된다() {
        User user = createUser("region-pass@example.com", "region-pass-1");
        Region region = seoulRegionOrCreate();
        UserProfile profile = createProfile(user, region);
        Policy policy = createPolicy(RegionScope.REGIONAL);
        policyRegionRepository.save(PolicyRegion.builder().policy(policy).region(region).build());
        PolicyEligibility eligibility = policyEligibilityRepository.save(
                PolicyEligibility.builder().policy(policy).build());
        when(policyEligibilityEvaluator.evaluate(any(), any(), any(), any()))
                .thenReturn(new PolicyEligibilityResult(EligibilityStatus.ELIGIBLE, "테스트", List.of()));

        policyEligibilityService.evaluate(user.getId(), policy.getId());

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(policyEligibilityEvaluator).evaluate(eq(profile), eq(policy), captor.capture(), eq(eligibility));
        assertThat(captor.getValue()).containsExactly(region.getId());
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
                .gender(Gender.FEMALE)
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

    private Policy createPolicy(RegionScope regionScope) {
        Organization organization = Organization.builder()
                .name("테스트기관")
                .type("중앙부처")
                .build();
        entityManager.persist(organization);

        Policy policy = Policy.builder()
                .organization(organization)
                .title("테스트 정책")
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(regionScope)
                .status(PolicyStatus.OPEN)
                .build();
        return policyRepository.save(policy);
    }
}
