package com.mozip.server.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.PolicySummary;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.policy.repository.PolicySummaryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
@Import(PolicySummaryPersistenceService.class)
class PolicySummaryPersistenceServiceTest {

    @Autowired
    private PolicySummaryPersistenceService policySummaryPersistenceService;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicySummaryRepository policySummaryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void insertNew는_새_row를_생성한다() {
        Policy policy = savePolicy("신규 삽입 테스트 정책");

        PolicySummary saved = policySummaryPersistenceService.insertNew(policy.getId(), "생성된 요약", "a".repeat(64));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getContent()).isEqualTo("생성된 요약");
        assertThat(policySummaryRepository.findByPolicyId(policy.getId())).isPresent();
    }

    @Test
    void insertNew를_같은_policyId로_두_번_호출하면_실제_DataIntegrityViolationException이_발생한다() {
        Policy policy = savePolicy("동시 삽입 경합 테스트 정책");
        policySummaryPersistenceService.insertNew(policy.getId(), "첫 번째 요약", "a".repeat(64));

        assertThatThrownBy(() -> policySummaryPersistenceService.insertNew(policy.getId(), "두 번째 요약", "b".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updateExisting은_기존_row의_content와_sourceHash를_갱신한다() {
        Policy policy = savePolicy("갱신 테스트 정책");
        PolicySummary existing = PolicySummary.builder()
                .policy(policy).content("이전 요약").sourceHash("a".repeat(64)).build();
        entityManager.persist(existing);
        entityManager.flush();
        entityManager.clear();

        policySummaryPersistenceService.updateExisting(existing.getId(), "갱신된 요약", "b".repeat(64));

        PolicySummary updated = policySummaryRepository.findByPolicyId(policy.getId()).orElseThrow();
        assertThat(updated.getContent()).isEqualTo("갱신된 요약");
        assertThat(updated.getSourceHash()).isEqualTo("b".repeat(64));
    }

    private Policy savePolicy(String title) {
        Organization organization = Organization.builder().name("테스트기관").build();
        entityManager.persist(organization);
        return policyRepository.save(Policy.builder()
                .organization(organization)
                .title(title)
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.ALWAYS_OPEN)
                .build());
    }
}
