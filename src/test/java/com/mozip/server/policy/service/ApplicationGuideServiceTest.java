package com.mozip.server.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mozip.server.ai.dto.ApplicationGuideStep;
import com.mozip.server.ai.service.PolicyApplicationGuideService;
import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.dto.ApplicationGuideResponse;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyApplicationInfo;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.evaluator.PolicyAvailabilityEvaluator;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyApplicationInfoRepository;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicationGuideServiceTest {

    private final PolicyRepository policyRepository = mock(PolicyRepository.class);
    private final PolicyEligibilityRepository policyEligibilityRepository = mock(PolicyEligibilityRepository.class);
    private final PolicyApplicationInfoRepository policyApplicationInfoRepository =
            mock(PolicyApplicationInfoRepository.class);
    private final PolicyAvailabilityEvaluator policyAvailabilityEvaluator = mock(PolicyAvailabilityEvaluator.class);
    private final PolicyApplicationGuideService policyApplicationGuideService = mock(PolicyApplicationGuideService.class);

    private final ApplicationGuideService applicationGuideService = new ApplicationGuideService(
            policyRepository, policyEligibilityRepository, policyApplicationInfoRepository,
            policyAvailabilityEvaluator, policyApplicationGuideService);

    @Test
    void 존재하지_않는_정책이면_PolicyNotFoundException을_던진다() {
        when(policyRepository.findWithOrganizationById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationGuideService.getApplicationGuide(999L))
                .isInstanceOf(PolicyNotFoundException.class);
    }

    @Test
    void applicationInfo가_없고_applicationMethod가_있으면_AI를_호출하지_않고_단일_step으로_구성한다() {
        Policy policy = policy(1L, "워크넷 온라인 신청", null);
        when(policyRepository.findWithOrganizationById(1L)).thenReturn(Optional.of(policy));
        when(policyEligibilityRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(policyApplicationInfoRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(policyAvailabilityEvaluator.evaluate(policy)).thenReturn(availabilityResult());

        ApplicationGuideResponse response = applicationGuideService.getApplicationGuide(1L);

        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().get(0).title()).isEqualTo("신청 방법");
        assertThat(response.steps().get(0).description()).isEqualTo("워크넷 온라인 신청");
        assertThat(response.requiredDocuments()).isEmpty();
        verify(policyApplicationGuideService, never()).generate(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void applicationInfo도_없고_applicationMethod도_없으면_steps가_빈_배열이다() {
        Policy policy = policy(1L, null, null);
        when(policyRepository.findWithOrganizationById(1L)).thenReturn(Optional.of(policy));
        when(policyEligibilityRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(policyApplicationInfoRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(policyAvailabilityEvaluator.evaluate(policy)).thenReturn(availabilityResult());

        ApplicationGuideResponse response = applicationGuideService.getApplicationGuide(1L);

        assertThat(response.steps()).isEmpty();
        assertThat(response.requiredDocuments()).isEmpty();
        verify(policyApplicationGuideService, never()).generate(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void applicationInfo가_있고_procedure가_있으면_procedure를_AI_source로_사용한다() {
        Policy policy = policy(1L, "워크넷 온라인 신청", null);
        PolicyApplicationInfo applicationInfo = applicationInfo(policy, "고용센터 방문 또는 온라인 신청", "취업지원신청서");
        when(policyRepository.findWithOrganizationById(1L)).thenReturn(Optional.of(policy));
        when(policyEligibilityRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(policyApplicationInfoRepository.findByPolicyId(1L)).thenReturn(Optional.of(applicationInfo));
        when(policyAvailabilityEvaluator.evaluate(policy)).thenReturn(availabilityResult());
        when(policyApplicationGuideService.generate("고용센터 방문 또는 온라인 신청", "취업지원신청서"))
                .thenReturn(new com.mozip.server.ai.dto.ApplicationGuideResponse(
                        List.of(new ApplicationGuideStep(1, "신청", "설명")), List.of("취업지원신청서")));

        ApplicationGuideResponse response = applicationGuideService.getApplicationGuide(1L);

        verify(policyApplicationGuideService).generate("고용센터 방문 또는 온라인 신청", "취업지원신청서");
        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().get(0).title()).isEqualTo("신청");
    }

    @Test
    void applicationInfo는_있지만_procedure가_없으면_applicationMethod로_대체해_AI를_호출한다() {
        Policy policy = policy(1L, "워크넷 온라인 신청", null);
        PolicyApplicationInfo applicationInfo = applicationInfo(policy, null, "취업지원신청서");
        when(policyRepository.findWithOrganizationById(1L)).thenReturn(Optional.of(policy));
        when(policyEligibilityRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(policyApplicationInfoRepository.findByPolicyId(1L)).thenReturn(Optional.of(applicationInfo));
        when(policyAvailabilityEvaluator.evaluate(policy)).thenReturn(availabilityResult());
        when(policyApplicationGuideService.generate(eq("워크넷 온라인 신청"), eq("취업지원신청서")))
                .thenReturn(new com.mozip.server.ai.dto.ApplicationGuideResponse(
                        List.of(new ApplicationGuideStep(1, "신청", "설명")), List.of()));

        applicationGuideService.getApplicationGuide(1L);

        verify(policyApplicationGuideService).generate("워크넷 온라인 신청", "취업지원신청서");
    }

    @Test
    void instructions_source가_없고_requiredDocumentsText만_있으면_AI를_호출하지_않고_원문을_그대로_보존한다() {
        Policy policy = policy(1L, null, null);
        PolicyApplicationInfo applicationInfo = applicationInfo(policy, null, "취업지원신청서 및 개인정보 동의서 원문");
        when(policyRepository.findWithOrganizationById(1L)).thenReturn(Optional.of(policy));
        when(policyEligibilityRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(policyApplicationInfoRepository.findByPolicyId(1L)).thenReturn(Optional.of(applicationInfo));
        when(policyAvailabilityEvaluator.evaluate(policy)).thenReturn(availabilityResult());

        ApplicationGuideResponse response = applicationGuideService.getApplicationGuide(1L);

        assertThat(response.steps()).isEmpty();
        assertThat(response.requiredDocuments()).containsExactly("취업지원신청서 및 개인정보 동의서 원문");
        verify(policyApplicationGuideService, never()).generate(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void eligibility가_없으면_requirements는_null이고_targetDescription은_그대로_노출된다() {
        Policy policy = policy(1L, "워크넷 온라인 신청", "만 15세 이상 청년");
        when(policyRepository.findWithOrganizationById(1L)).thenReturn(Optional.of(policy));
        when(policyEligibilityRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(policyApplicationInfoRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(policyAvailabilityEvaluator.evaluate(policy)).thenReturn(availabilityResult());

        ApplicationGuideResponse response = applicationGuideService.getApplicationGuide(1L);

        assertThat(response.requirements()).isNull();
        assertThat(response.requirementsDescription()).isEqualTo("만 15세 이상 청년");
    }

    @Test
    void eligibility가_있으면_requirements에_구조화된_값이_채워진다() {
        Policy policy = policy(1L, "워크넷 온라인 신청", "만 15세 이상 청년");
        PolicyEligibility eligibility = PolicyEligibility.builder()
                .policy(policy).minimumAge(15).maximumAge(34).genderCondition(null).build();
        when(policyRepository.findWithOrganizationById(1L)).thenReturn(Optional.of(policy));
        when(policyEligibilityRepository.findByPolicyId(1L)).thenReturn(Optional.of(eligibility));
        when(policyApplicationInfoRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(policyAvailabilityEvaluator.evaluate(policy)).thenReturn(availabilityResult());

        ApplicationGuideResponse response = applicationGuideService.getApplicationGuide(1L);

        assertThat(response.requirements()).isNotNull();
        assertThat(response.requirements().minimumAge()).isEqualTo(15);
        assertThat(response.requirements().maximumAge()).isEqualTo(34);
        assertThat(response.requirementsDescription()).isEqualTo("만 15세 이상 청년");
    }

    @Test
    void availability는_기존_evaluator_결과를_그대로_사용한다() {
        Policy policy = policy(1L, "워크넷 온라인 신청", null);
        when(policyRepository.findWithOrganizationById(1L)).thenReturn(Optional.of(policy));
        when(policyEligibilityRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(policyApplicationInfoRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(policyAvailabilityEvaluator.evaluate(policy)).thenReturn(
                new PolicyAvailabilityResult(PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.WITHIN_APPLICATION_PERIOD, true));

        ApplicationGuideResponse response = applicationGuideService.getApplicationGuide(1L);

        assertThat(response.availability().status()).isEqualTo(PolicyAvailability.AVAILABLE);
        assertThat(response.availability().reason()).isEqualTo(PolicyAvailabilityReason.WITHIN_APPLICATION_PERIOD);
        assertThat(response.availability().closingSoon()).isTrue();
    }

    @Test
    void URL_문의처_주의사항은_applicationInfo에서_그대로_전달된다() {
        Policy policy = policy(1L, "워크넷 온라인 신청", null);
        PolicyApplicationInfo applicationInfo = applicationInfo(policy, "절차 원문", "서류 원문");
        when(policyRepository.findWithOrganizationById(1L)).thenReturn(Optional.of(policy));
        when(policyEligibilityRepository.findByPolicyId(1L)).thenReturn(Optional.empty());
        when(policyApplicationInfoRepository.findByPolicyId(1L)).thenReturn(Optional.of(applicationInfo));
        when(policyAvailabilityEvaluator.evaluate(policy)).thenReturn(availabilityResult());
        when(policyApplicationGuideService.generate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.mozip.server.ai.dto.ApplicationGuideResponse(List.of(new ApplicationGuideStep(1, "신청", "설명")), List.of()));

        ApplicationGuideResponse response = applicationGuideService.getApplicationGuide(1L);

        assertThat(response.applicationUrl()).isEqualTo("https://www.work24.go.kr");
        assertThat(response.contactInfo()).isEqualTo("고용노동부 고객상담센터 1350");
        assertThat(response.notes()).isEqualTo("동절기 요금이 가장 많이 나오는 에너지원을 선택해 신청합니다.");
    }

    private Policy policy(Long id, String applicationMethod, String targetDescription) {
        Organization organization = Organization.builder().name("테스트기관").build();
        Policy policy = Policy.builder()
                .organization(organization)
                .title("테스트정책")
                .targetDescription(targetDescription)
                .applicationMethod(applicationMethod)
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.ALWAYS_OPEN)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);
        return policy;
    }

    private PolicyApplicationInfo applicationInfo(Policy policy, String applicationProcedure, String requiredDocumentsText) {
        return PolicyApplicationInfo.builder()
                .policy(policy)
                .applicationProcedure(applicationProcedure)
                .requiredDocumentsText(requiredDocumentsText)
                .applicationUrl("https://www.work24.go.kr")
                .contactInfo("고용노동부 고객상담센터 1350")
                .applicationNotes("동절기 요금이 가장 많이 나오는 에너지원을 선택해 신청합니다.")
                .sourceUrl("https://www.gov.kr/portal/rcvfvrSvc/dtlEx/149200005007")
                .verifiedAt(LocalDateTime.of(2026, 8, 18, 15, 50))
                .build();
    }

    private PolicyAvailabilityResult availabilityResult() {
        return new PolicyAvailabilityResult(PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.ALWAYS_OPEN, false);
    }
}
