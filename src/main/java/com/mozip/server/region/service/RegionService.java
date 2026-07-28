package com.mozip.server.region.service;

import com.mozip.server.region.dto.RegionResponse;
import com.mozip.server.region.repository.RegionRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RegionService {

    private final RegionRepository regionRepository;

    public RegionService(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    public List<RegionResponse> getRegions() {
        return regionRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(RegionResponse::from)
                .toList();
    }
}
