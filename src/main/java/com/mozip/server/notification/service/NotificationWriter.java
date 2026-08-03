package com.mozip.server.notification.service;

import com.mozip.server.notification.entity.Notification;
import com.mozip.server.notification.repository.NotificationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationWriter {

    private final NotificationRepository notificationRepository;

    public NotificationWriter(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(Notification notification) {
        notificationRepository.saveAndFlush(notification);
    }
}
