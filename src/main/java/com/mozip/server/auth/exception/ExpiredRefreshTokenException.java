package com.mozip.server.auth.exception;

import com.mozip.server.global.exception.BusinessException;
import com.mozip.server.global.exception.ErrorCode;

public class ExpiredRefreshTokenException extends BusinessException {

    public ExpiredRefreshTokenException() {
        super(ErrorCode.EXPIRED_REFRESH_TOKEN);
    }
}