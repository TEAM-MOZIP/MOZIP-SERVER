package com.mozip.server.notification.exception;

import com.mozip.server.global.exception.BusinessException;
import com.mozip.server.global.exception.ErrorCode;

public class NotificationNotFoundException extends BusinessException {

    public NotificationNotFoundException(Long notificationId) {
        super(ErrorCode.NOTIFICATION_NOT_FOUND, "알림을 찾을 수 없습니다. id=" + notificationId);
    }
}
