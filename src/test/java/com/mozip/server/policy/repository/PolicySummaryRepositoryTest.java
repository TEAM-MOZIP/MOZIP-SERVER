package com.mozip.server.policy.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.PolicySummary;
import com.mozip.server.policy.entity.RegionScope;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
class PolicySummaryRepositoryTest {

    @Autowired
    private PolicySummaryRepository policySummaryRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 저장된_요약은_policyId로_조회된다() {
        Policy policy = savePolicy("요약 있는 정책");
        PolicySummary summary = PolicySummary.builder()
                .policy(policy)
                .content("요약 문단입니다.")
                .sourceHash("a".repeat(64))
                .build();
        entityManager.persist(summary);
        entityManager.flush();
        entityManager.clear();

        Optional<PolicySummary> found = policySummaryRepository.findByPolicyId(policy.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getContent()).isEqualTo("요약 문단입니다.");
        assertThat(found.get().getSourceHash()).isEqualTo("a".repeat(64));
        assertThat(found.get().getPolicy().getId()).isEqualTo(policy.getId());
    }

    @Test
    void 요약이_없는_정책은_빈_Optional을_반환한다() {
        Policy policy = savePolicy("요약 없는 정책");

        Optional<PolicySummary> found = policySummaryRepository.findByPolicyId(policy.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void 같은_정책에_두_번째_row를_저장하면_unique_제약_위반이다() {
        Policy policy = savePolicy("중복 저장 테스트 정책");
        entityManager.persist(PolicySummary.builder()
                .policy(policy).content("첫 번째 요약").sourceHash("a".repeat(64)).build());
        entityManager.flush();

        assertThatThrownBy(() -> {
            entityManager.persist(PolicySummary.builder()
                    .policy(policy).content("두 번째 요약").sourceHash("b".repeat(64)).build());
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void update_호출_후_재조회하면_새_content와_sourceHash가_반영된다() {
        Policy policy = savePolicy("갱신 테스트 정책");
        PolicySummary summary = PolicySummary.builder()
                .policy(policy).content("이전 요약").sourceHash("a".repeat(64)).build();
        entityManager.persist(summary);
        entityManager.flush();

        summary.update("새 요약", "b".repeat(64));
        entityManager.flush();
        entityManager.clear();

        PolicySummary updated = policySummaryRepository.findByPolicyId(policy.getId()).orElseThrow();
        assertThat(updated.getContent()).isEqualTo("새 요약");
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
