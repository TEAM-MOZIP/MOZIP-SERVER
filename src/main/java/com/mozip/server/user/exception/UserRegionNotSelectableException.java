package com.mozip.server.user.exception;

import com.mozip.server.global.exception.BusinessException;
import com.mozip.server.global.exception.ErrorCode;

public class UserRegionNotSelectableException extends BusinessException {

    public UserRegionNotSelectableException(Long regionId) {
        super(ErrorCode.USER_REGION_NOT_SELECTABLE, "사용자 지역으로 선택할 수 없습니다. id=" + regionId);
    }
}
