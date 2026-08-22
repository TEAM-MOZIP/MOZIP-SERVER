package com.mozip.server.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mozip.server.ai.exception.ChatResponseUnavailableException;
import com.mozip.server.auth.config.CustomAccessDeniedHandler;
import com.mozip.server.auth.config.CustomAuthenticationEntryPoint;
import com.mozip.server.auth.config.SecurityConfig;
import com.mozip.server.auth.jwt.JwtTokenProvider;
import com.mozip.server.chat.dto.ChatRequest;
import com.mozip.server.chat.dto.ChatResponse;
import com.mozip.server.chat.service.ChatService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatService chatService;

    @Test
    void 인증_없이_요청하면_401이다() throws Exception {
        mockMvc.perform(post("/api/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("안녕"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증된_사용자는_챗봇_응답을_받는다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        ChatResponse response = new ChatResponse("답변입니다.", List.of(), List.of());
        when(chatService.handle(any())).thenReturn(response);

        mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("서울 사는 취준생이 받을 정책 있어?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("답변입니다."));
    }

    @Test
    void message가_공백이면_400이다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");

        mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 챗봇_응답_생성에_실패하면_503이다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        when(chatService.handle(any())).thenThrow(new ChatResponseUnavailableException());

        mockMvc.perform(post("/api/chat/messages")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("서울 사는 취준생이 받을 정책 있어?"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CHAT_RESPONSE_UNAVAILABLE"));
    }
}
