package com.mozip.server.policy.exception;

import com.mozip.server.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public class PolicyNotFoundException extends RuntimeException {

    private final ErrorCode errorCode;

    public PolicyNotFoundException(Long policyId) {
        super("정책을 찾을 수 없습니다. id=" + policyId);
        this.errorCode = ErrorCode.POLICY_NOT_FOUND;
    }
}