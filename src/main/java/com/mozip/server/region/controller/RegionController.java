package com.mozip.server.region.controller;

import com.mozip.server.region.dto.RegionResponse;
import com.mozip.server.region.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Region", description = "지역 조회 API")
@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionService regionService;

    public RegionController(RegionService regionService) {
        this.regionService = regionService;
    }

    @Operation(summary = "지역 목록 조회", description = "전체 지역 목록을 id 오름차순으로 조회한다.")
    @GetMapping
    public List<RegionResponse> getRegions() {
        return regionService.getRegions();
    }
}
