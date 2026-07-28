package com.mozip.server.region.dto;

import com.mozip.server.region.entity.Region;

public record RegionResponse(
        Long id,
        String code,
        String name
) {

    public static RegionResponse from(Region region) {
        return new RegionResponse(region.getId(), region.getCode(), region.getName());
    }
}
