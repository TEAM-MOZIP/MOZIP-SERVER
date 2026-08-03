package com.mozip.server.notification.scheduler;

import com.mozip.server.notification.service.NotificationBatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "notification.scheduling", name = "enabled", havingValue = "true")
public class NotificationScheduler {

    private final NotificationBatchService notificationBatchService;

    public NotificationScheduler(NotificationBatchService notificationBatchService) {
        this.notificationBatchService = notificationBatchService;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void generateClosingSoonNotifications() {
        notificationBatchService.generateClosingSoonNotifications();
    }
}
