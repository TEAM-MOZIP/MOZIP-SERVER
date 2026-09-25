package com.mozip.server.policy.exception;

import com.mozip.server.global.exception.BusinessException;
import com.mozip.server.global.exception.ErrorCode;

public class PolicyPackageNotFoundException extends BusinessException {

    public PolicyPackageNotFoundException(String packageId) {
        super(ErrorCode.POLICY_PACKAGE_NOT_FOUND, "정책 패키지를 찾을 수 없습니다. packageId=" + packageId);
    }

    public PolicyPackageNotFoundException(String packageId, String sectionKey) {
        super(ErrorCode.POLICY_PACKAGE_NOT_FOUND,
                "정책 패키지 섹션을 찾을 수 없습니다. packageId=" + packageId + ", sectionKey=" + sectionKey);
    }
}
