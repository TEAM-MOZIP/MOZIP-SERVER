package com.mozip.server.notification.controller;

import com.mozip.server.notification.dto.NotificationResponse;
import com.mozip.server.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "내 알림 목록 조회", description = "인증된 사용자 본인의 알림을 최신순으로 전체 조회한다.")
    @GetMapping
    public List<NotificationResponse> getMyNotifications(@AuthenticationPrincipal String userId) {
        return notificationService.getMyNotifications(Long.valueOf(userId));
    }

    @Operation(summary = "알림 읽음 처리", description = "인증된 사용자 본인의 알림을 읽음 처리한다. 이미 읽은 알림도 동일하게 처리된다.")
    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@AuthenticationPrincipal String userId, @PathVariable Long notificationId) {
        notificationService.markAsRead(Long.valueOf(userId), notificationId);
    }
}
