package com.mozip.server.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.dto.PolicyDetailResponse;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.dto.PolicySummaryResponse;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class PolicyServiceTest {

    private static final String KEYWORD = "가용성테스트정책";

    @Autowired
    private PolicyService policyService;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 목록_조회_시_AVAILABLE_정책과_UNAVAILABLE_정책이_각각_올바르게_응답된다() {
        Policy availablePolicy = createPolicy(KEYWORD + "-상시", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS,
                null, null);
        Policy unavailablePolicy = createPolicy(KEYWORD + "-마감", PolicyStatus.OPEN, ApplicationType.PERIOD,
                LocalDate.now().minusYears(1), LocalDate.now().minusMonths(1));

        PageResponse<PolicySummaryResponse> response = policyService.searchPolicies(
                new PolicySearchRequest(KEYWORD, null, null, null), PageRequest.of(0, 20));

        assertThat(response.content()).hasSize(2);
        assertThat(findById(response, availablePolicy.getId()).availability().status())
                .isEqualTo(PolicyAvailability.AVAILABLE);
        assertThat(findById(response, unavailablePolicy.getId()).availability().status())
                .isEqualTo(PolicyAvailability.UNAVAILABLE);
    }

    @Test
    void 상세_조회_시_availability가_올바르게_응답된다() {
        Policy policy = createPolicy(KEYWORD + "-상세", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);

        PolicyDetailResponse response = policyService.getPolicyDetail(policy.getId());

        assertThat(response.availability().status()).isEqualTo(PolicyAvailability.AVAILABLE);
    }

    @Test
    void 존재하지_않는_정책_상세_조회_시_예외가_발생한다() {
        assertThatThrownBy(() -> policyService.getPolicyDetail(999999L))
                .isInstanceOf(PolicyNotFoundException.class);
    }

    private PolicySummaryResponse findById(PageResponse<PolicySummaryResponse> response, Long policyId) {
        return response.content().stream()
                .filter(summary -> summary.id().equals(policyId))
                .findFirst()
                .orElseThrow();
    }

    private Policy createPolicy(String title, PolicyStatus status, ApplicationType applicationType,
                                 LocalDate startDate, LocalDate endDate) {
        Organization organization = Organization.builder()
                .name("테스트기관")
                .type("중앙부처")
                .build();
        entityManager.persist(organization);

        Policy policy = Policy.builder()
                .organization(organization)
                .title(title)
                .applicationType(applicationType)
                .applicationStartDate(startDate)
                .applicationEndDate(endDate)
                .regionScope(RegionScope.NATIONAL)
                .status(status)
                .build();
        return policyRepository.save(policy);
    }
}
