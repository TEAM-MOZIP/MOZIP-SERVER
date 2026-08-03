package com.mozip.server.bookmark.exception;

import com.mozip.server.global.exception.BusinessException;
import com.mozip.server.global.exception.ErrorCode;

public class InvalidSortFieldException extends BusinessException {

    public InvalidSortFieldException(String field) {
        super(ErrorCode.INVALID_REQUEST, "정렬 기준으로 사용할 수 없는 필드입니다. field=" + field);
    }
}
