package com.mozip.server.user.exception;

import com.mozip.server.global.exception.BusinessException;
import com.mozip.server.global.exception.ErrorCode;

public class UserProfileAlreadyExistsException extends BusinessException {

    public UserProfileAlreadyExistsException(Long userId) {
        super(ErrorCode.USER_PROFILE_ALREADY_EXISTS, "이미 존재하는 사용자 프로필입니다. userId=" + userId);
    }
}
