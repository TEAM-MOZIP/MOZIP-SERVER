package com.mozip.server.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mozip.server.ai.client.ChatResponseClient;
import com.mozip.server.ai.dto.ChatResponseRequest;
import com.mozip.server.ai.dto.ChatResponseResponse;
import com.mozip.server.ai.dto.ChatTurn;
import com.mozip.server.ai.exception.ChatResponseUnavailableException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;

class ChatResponseGenerationServiceTest {

    private final ChatResponseClient chatResponseClient = mock(ChatResponseClient.class);
    private final ChatResponseGenerationService chatResponseGenerationService =
            new ChatResponseGenerationService(chatResponseClient);

    @Test
    void AI_응답이_성공이면_reply를_반환한다() {
        when(chatResponseClient.respond(any())).thenReturn(new ChatResponseResponse("답변입니다."));

        String reply = chatResponseGenerationService.generate("질문", List.of(), null, List.of(), List.of());

        assertThat(reply).isEqualTo("답변입니다.");
    }

    @Test
    void history가_요청에_그대로_포함된다() {
        when(chatResponseClient.respond(any())).thenReturn(new ChatResponseResponse("답변입니다."));
        List<ChatTurn> history = List.of(new ChatTurn("국민취업지원제도 알려줘", "국민취업지원제도는 ~ 제도입니다."));

        chatResponseGenerationService.generate("신청 기간은?", List.of(), null, List.of(), history);

        ArgumentCaptor<ChatResponseRequest> requestCaptor = ArgumentCaptor.forClass(ChatResponseRequest.class);
        verify(chatResponseClient).respond(requestCaptor.capture());
        assertThat(requestCaptor.getValue().history()).isEqualTo(history);
    }

    @Test
    void AI_호출이_RestClientException을_던지면_ChatResponseUnavailableException을_던진다() {
        when(chatResponseClient.respond(any())).thenThrow(new ResourceAccessException("연결 실패"));

        assertThatThrownBy(() -> chatResponseGenerationService.generate("질문", List.of(), null, List.of(), List.of()))
                .isInstanceOf(ChatResponseUnavailableException.class);
    }

    @Test
    void 응답이_null이면_ChatResponseUnavailableException을_던진다() {
        when(chatResponseClient.respond(any())).thenReturn(null);

        assertThatThrownBy(() -> chatResponseGenerationService.generate("질문", List.of(), null, List.of(), List.of()))
                .isInstanceOf(ChatResponseUnavailableException.class);
    }

    @Test
    void reply가_공백문자열이면_ChatResponseUnavailableException을_던진다() {
        when(chatResponseClient.respond(any())).thenReturn(new ChatResponseResponse("   "));

        assertThatThrownBy(() -> chatResponseGenerationService.generate("질문", List.of(), null, List.of(), List.of()))
                .isInstanceOf(ChatResponseUnavailableException.class);
    }
}
