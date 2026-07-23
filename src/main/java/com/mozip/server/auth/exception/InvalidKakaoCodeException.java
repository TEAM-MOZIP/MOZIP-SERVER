package com.mozip.server.auth.exception;

import com.mozip.server.global.exception.BusinessException;
import com.mozip.server.global.exception.ErrorCode;

public class InvalidKakaoCodeException extends BusinessException {

    public InvalidKakaoCodeException() {
        super(ErrorCode.INVALID_KAKAO_CODE);
    }
}