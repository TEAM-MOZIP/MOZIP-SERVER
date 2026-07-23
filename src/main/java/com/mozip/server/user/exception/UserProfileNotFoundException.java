package com.mozip.server.user.exception;

import com.mozip.server.global.exception.BusinessException;
import com.mozip.server.global.exception.ErrorCode;

public class UserProfileNotFoundException extends BusinessException {

    public UserProfileNotFoundException(Long userId) {
        super(ErrorCode.USER_PROFILE_NOT_FOUND, "사용자 프로필을 찾을 수 없습니다. userId=" + userId);
    }
}
