package com.mozip.server.bookmark.exception;

import com.mozip.server.global.exception.BusinessException;
import com.mozip.server.global.exception.ErrorCode;

public class BookmarkAlreadyExistsException extends BusinessException {

    public BookmarkAlreadyExistsException(Long userId, Long policyId) {
        super(ErrorCode.BOOKMARK_ALREADY_EXISTS, "이미 북마크한 정책입니다. userId=" + userId + ", policyId=" + policyId);
    }
}
