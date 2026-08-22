package com.mozip.server.ai.exception;

import com.mozip.server.global.exception.BusinessException;
import com.mozip.server.global.exception.ErrorCode;

public class ChatResponseUnavailableException extends BusinessException {

    public ChatResponseUnavailableException() {
        super(ErrorCode.CHAT_RESPONSE_UNAVAILABLE);
    }
}
