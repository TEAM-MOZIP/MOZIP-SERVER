package com.mozip.server.region.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.region.dto.RegionResponse;
import com.mozip.server.region.entity.Region;
import com.mozip.server.region.repository.RegionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class RegionServiceTest {

    @Autowired
    private RegionService regionService;

    @Autowired
    private RegionRepository regionRepository;

    @Test
    void 지역_응답_필드가_올바르게_매핑된다() {
        Region region = regionRepository.save(Region.builder()
                .code("REGION_TEST_MAPPING")
                .name("지역응답테스트")
                .build());

        RegionResponse response = regionService.getRegions().stream()
                .filter(item -> item.id().equals(region.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(response.code()).isEqualTo("REGION_TEST_MAPPING");
        assertThat(response.name()).isEqualTo("지역응답테스트");
    }

    @Test
    void 지역_목록은_id_오름차순으로_정렬된다() {
        regionRepository.save(Region.builder()
                .code("REGION_TEST_SORT_1")
                .name("지역정렬테스트1")
                .build());
        regionRepository.save(Region.builder()
                .code("REGION_TEST_SORT_2")
                .name("지역정렬테스트2")
                .build());

        List<Long> ids = regionService.getRegions().stream()
                .map(RegionResponse::id)
                .toList();

        assertThat(ids).isSorted();
    }
}
