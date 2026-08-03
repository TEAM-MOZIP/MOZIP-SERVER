package com.mozip.server.notification.dto;

import com.mozip.server.notification.entity.Notification;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        Long policyId,
        String title,
        String content,
        boolean read,
        LocalDateTime createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getPolicy().getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
