package com.mozip.server.auth.exception;

import com.mozip.server.global.exception.BusinessException;
import com.mozip.server.global.exception.ErrorCode;

public class DuplicateKakaoEmailException extends BusinessException {

    public DuplicateKakaoEmailException() {
        super(ErrorCode.DUPLICATE_KAKAO_EMAIL);
    }
}
