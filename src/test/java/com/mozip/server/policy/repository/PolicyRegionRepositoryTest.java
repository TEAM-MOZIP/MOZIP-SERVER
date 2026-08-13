package com.mozip.server.policy.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyRegion;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.region.entity.Region;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
class PolicyRegionRepositoryTest {

    @Autowired
    private PolicyRegionRepository policyRegionRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findByPolicyIdIn으로_조회한_PolicyRegion은_Region이_이미_초기화되어_있다() {
        Organization organization = Organization.builder().name("N+1테스트기관").build();
        entityManager.persist(organization);

        Region region = Region.builder().code("N1_TEST_REGION").name("N+1테스트지역").build();
        entityManager.persist(region);

        Policy policy = policyRepository.save(Policy.builder()
                .organization(organization)
                .title("N+1테스트정책")
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(RegionScope.REGIONAL)
                .status(PolicyStatus.ALWAYS_OPEN)
                .build());
        entityManager.persist(PolicyRegion.builder().policy(policy).region(region).build());

        // 영속성 컨텍스트 1차 캐시가 아니라 실제 조회 결과의 fetch 상태를 검증하기 위해 flush 후 캐시를 비운다.
        entityManager.flush();
        entityManager.clear();

        List<PolicyRegion> found = policyRegionRepository.findByPolicyIdIn(List.of(policy.getId()));

        assertThat(found).hasSize(1);
        assertThat(Hibernate.isInitialized(found.get(0).getRegion())).isTrue();
        assertThat(found.get(0).getRegion().getCode()).isEqualTo("N1_TEST_REGION");
    }
}
