package com.mozip.server.chat.controller;

import com.mozip.server.chat.dto.ChatRequest;
import com.mozip.server.chat.dto.ChatResponse;
import com.mozip.server.chat.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "챗봇 API")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "챗봇 메시지 전송",
            description = "로그인한 사용자가 자연어 메시지를 보내면 조건 기반 정책 탐색 또는 정책 Q&A 응답을 받는다.")
    @PostMapping("/messages")
    public ChatResponse sendMessage(@RequestBody @Valid ChatRequest request) {
        return chatService.handle(request);
    }
}
