package com.mozip.server.region.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.region.entity.Region;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
class RegionRepositoryTest {

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void code로_지역을_조회한다() {
        Region region = Region.builder().code("CHAT_TEST_REGION_CODE").name("챗봇 테스트 지역").build();
        entityManager.persist(region);
        entityManager.flush();
        entityManager.clear();

        Optional<Region> found = regionRepository.findByCode("CHAT_TEST_REGION_CODE");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("챗봇 테스트 지역");
    }

    @Test
    void 존재하지_않는_code면_빈_Optional을_반환한다() {
        Optional<Region> found = regionRepository.findByCode("NOT_EXISTING_REGION_CODE");

        assertThat(found).isEmpty();
    }
}
