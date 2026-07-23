package com.mozip.server.region.exception;

import com.mozip.server.global.exception.BusinessException;
import com.mozip.server.global.exception.ErrorCode;

public class RegionNotFoundException extends BusinessException {

    public RegionNotFoundException(Long regionId) {
        super(ErrorCode.REGION_NOT_FOUND, "지역을 찾을 수 없습니다. id=" + regionId);
    }
}
