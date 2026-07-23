package com.mozip.server.policy.exception;

import com.mozip.server.global.exception.BusinessException;
import com.mozip.server.global.exception.ErrorCode;

public class PolicyNotFoundException extends BusinessException {

    public PolicyNotFoundException(Long policyId) {
        super(ErrorCode.POLICY_NOT_FOUND, "정책을 찾을 수 없습니다. id=" + policyId);
    }
}
