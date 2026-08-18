package com.mozip.server.ai.exception;

import com.mozip.server.global.exception.BusinessException;
import com.mozip.server.global.exception.ErrorCode;

public class TermExplanationUnavailableException extends BusinessException {

    public TermExplanationUnavailableException() {
        super(ErrorCode.TERM_EXPLANATION_UNAVAILABLE);
    }
}
