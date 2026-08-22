package com.mozip.server.ai.service;

import com.mozip.server.ai.client.ChatResponseClient;
import com.mozip.server.ai.dto.ChatResponseRequest;
import com.mozip.server.ai.dto.ChatResponseResponse;
import com.mozip.server.ai.dto.ChatTurn;
import com.mozip.server.ai.dto.GroundingPolicy;
import com.mozip.server.ai.dto.PolicyDetailGrounding;
import com.mozip.server.ai.dto.UnresolvedCondition;
import com.mozip.server.ai.exception.ChatResponseUnavailableException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * reply는 AI 생성 결과 자체가 최종 사용자 응답이라 AI 없이 대체 가능한 raw fallback이
 * 존재하지 않는다 — B-3(TermExplanationUnavailableException)과 동일하게 실패를
 * 200으로 감추지 않고 예외로 전파한다.
 */
@Component
public class ChatResponseGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ChatResponseGenerationService.class);

    private final ChatResponseClient chatResponseClient;

    public ChatResponseGenerationService(ChatResponseClient chatResponseClient) {
        this.chatResponseClient = chatResponseClient;
    }

    public String generate(String message, List<GroundingPolicy> groundingPolicies, PolicyDetailGrounding policyDetail,
                            List<UnresolvedCondition> unresolvedConditions, List<ChatTurn> history) {
        ChatResponseRequest request =
                new ChatResponseRequest(message, groundingPolicies, policyDetail, unresolvedConditions, history);

        ChatResponseResponse response;
        try {
            response = chatResponseClient.respond(request);
        } catch (RestClientException e) {
            log.warn("챗봇 응답 생성 호출에 실패했습니다. exceptionType={}", e.getClass().getSimpleName());
            throw new ChatResponseUnavailableException();
        }

        if (response == null || response.reply() == null || response.reply().isBlank()) {
            log.warn("챗봇 응답 생성 결과가 비어 있습니다.");
            throw new ChatResponseUnavailableException();
        }

        return response.reply();
    }
}
