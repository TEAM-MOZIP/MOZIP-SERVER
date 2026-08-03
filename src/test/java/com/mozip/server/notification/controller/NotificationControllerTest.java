package com.mozip.server.notification.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mozip.server.auth.config.CustomAccessDeniedHandler;
import com.mozip.server.auth.config.CustomAuthenticationEntryPoint;
import com.mozip.server.auth.config.SecurityConfig;
import com.mozip.server.auth.jwt.JwtTokenProvider;
import com.mozip.server.notification.dto.NotificationResponse;
import com.mozip.server.notification.exception.NotificationNotFoundException;
import com.mozip.server.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void 인증_없이_목록을_조회하면_401이다() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증된_사용자는_알림_목록을_조회한다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        NotificationResponse item = new NotificationResponse(
                1L, 10L, "북마크한 정책이 곧 마감돼요", "청년내일채움공제 신청 마감까지 3일 남았어요.", false, LocalDateTime.now());
        when(notificationService.getMyNotifications(1L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notificationId").value(1))
                .andExpect(jsonPath("$[0].policyId").value(10))
                .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    void 인증_없이_읽음_처리하면_401이다() throws Exception {
        mockMvc.perform(patch("/api/notifications/1/read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 읽음_처리에_성공하면_204다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");

        mockMvc.perform(patch("/api/notifications/1/read").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void 존재하지_않는_알림을_읽음_처리하면_404다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        doThrow(new NotificationNotFoundException(999L)).when(notificationService).markAsRead(eq(1L), eq(999L));

        mockMvc.perform(patch("/api/notifications/999/read").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }
}
