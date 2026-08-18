package com.mozip.server.policy.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyApplicationInfo;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
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
class PolicyApplicationInfoRepositoryTest {

    @Autowired
    private PolicyApplicationInfoRepository policyApplicationInfoRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 큐레이션된_정책은_policyId로_조회된다() {
        Policy policy = savePolicy("상세정보 있는 정책");
        PolicyApplicationInfo info = PolicyApplicationInfo.builder()
                .policy(policy)
                .applicationProcedure("온라인 신청 후 서류 제출")
                .sourceUrl("https://www.example.go.kr/notice")
                .verifiedAt(LocalDateTime.of(2026, 8, 18, 0, 0))
                .build();
        entityManager.persist(info);
        entityManager.flush();
        entityManager.clear();

        Optional<PolicyApplicationInfo> found = policyApplicationInfoRepository.findByPolicyId(policy.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getApplicationProcedure()).isEqualTo("온라인 신청 후 서류 제출");
        assertThat(found.get().getPolicy().getId()).isEqualTo(policy.getId());
    }

    @Test
    void 상세정보가_없는_정책은_빈_Optional을_반환한다() {
        Policy policy = savePolicy("상세정보 없는 정책");

        Optional<PolicyApplicationInfo> found = policyApplicationInfoRepository.findByPolicyId(policy.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void 같은_정책에_두_번째_row를_저장하면_unique_제약_위반이다() {
        Policy policy = savePolicy("중복 저장 테스트 정책");
        entityManager.persist(PolicyApplicationInfo.builder()
                .policy(policy)
                .sourceUrl("https://www.example.go.kr/notice-1")
                .verifiedAt(LocalDateTime.of(2026, 8, 18, 0, 0))
                .build());
        entityManager.flush();

        assertThatThrownBy(() -> {
            entityManager.persist(PolicyApplicationInfo.builder()
                    .policy(policy)
                    .sourceUrl("https://www.example.go.kr/notice-2")
                    .verifiedAt(LocalDateTime.of(2026, 8, 18, 0, 0))
                    .build());
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void sourceUrl이_없으면_저장에_실패한다() {
        Policy policy = savePolicy("sourceUrl 누락 테스트 정책");

        assertThatThrownBy(() -> {
            entityManager.persist(PolicyApplicationInfo.builder()
                    .policy(policy)
                    .verifiedAt(LocalDateTime.of(2026, 8, 18, 0, 0))
                    .build());
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
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
