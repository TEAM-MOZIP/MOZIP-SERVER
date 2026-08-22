package com.mozip.server.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mozip.server.ai.dto.ChatResponseRequest;
import com.mozip.server.ai.dto.ChatResponseResponse;
import com.mozip.server.ai.dto.ChatTurn;
import com.mozip.server.ai.dto.ConditionAxis;
import com.mozip.server.ai.dto.GroundingPolicy;
import com.mozip.server.ai.dto.PolicyDetailGrounding;
import com.mozip.server.ai.dto.UnresolvedCondition;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class ChatResponseClientTest {

    private MockRestServiceServer mockServer;
    private ChatResponseClient chatResponseClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient mockedRestClient = builder.baseUrl("http://localhost:9999").build();
        chatResponseClient = new ChatResponseClient(mockedRestClient);
    }

    @Test
    void POST_요청으로_respond_엔드포인트를_호출한다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/chat/respond"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"reply":"서울 청년 대상 정책 2건을 찾았습니다."}
                        """, MediaType.APPLICATION_JSON));

        ChatResponseResponse response = chatResponseClient.respond(sampleRequest());

        assertThat(response.reply()).isEqualTo("서울 청년 대상 정책 2건을 찾았습니다.");
    }

    @Test
    void request_body가_계약대로_camelCase_필드명을_담는다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/chat/respond"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("\"message\":\"국민취업지원제도가 뭐야?\""),
                        Matchers.containsString("\"groundingPolicies\""),
                        Matchers.containsString("\"policyDetail\""),
                        Matchers.containsString("\"unresolvedConditions\""),
                        Matchers.containsString("\"history\"")
                )))
                .andRespond(withSuccess("""
                        {"reply":"ok"}
                        """, MediaType.APPLICATION_JSON));

        chatResponseClient.respond(sampleRequest());
    }

    @Test
    void unresolvedConditions의_axis가_AI_계약대로_소문자_snake_case로_직렬화된다() {
        ChatResponseRequest requestWithUnresolvedCondition = new ChatResponseRequest(
                "서울 사는 프리랜서인데 받을 수 있는 정책 있어?",
                List.of(),
                null,
                List.of(new UnresolvedCondition(ConditionAxis.EMPLOYMENT_STATUS, "프리랜서")),
                List.of()
        );

        mockServer.expect(requestTo("http://localhost:9999/api/v1/chat/respond"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("\"axis\":\"employment_status\""),
                        Matchers.not(Matchers.containsString("\"axis\":\"EMPLOYMENT_STATUS\""))
                )))
                .andRespond(withSuccess("""
                        {"reply":"ok"}
                        """, MediaType.APPLICATION_JSON));

        chatResponseClient.respond(requestWithUnresolvedCondition);
    }

    @Test
    void 클라이언트_오류_응답이면_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/chat/respond"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"VALIDATION_ERROR","message":"invalid","detail":null}
                                """));

        assertThatThrownBy(() -> chatResponseClient.respond(sampleRequest()))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void 서버_오류_응답이면_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/chat/respond"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> chatResponseClient.respond(sampleRequest()))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void malformed_response면_RestClientException_계열_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/chat/respond"))
                .andRespond(withSuccess("이건 JSON이 아님", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> chatResponseClient.respond(sampleRequest()))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    @Timeout(6)
    void properties에_설정한_explainTimeoutSeconds가_실제로_반영된다() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Thread acceptThread = new Thread(() -> {
                try {
                    serverSocket.accept();
                } catch (Exception ignored) {
                    // 테스트 종료 시 소켓이 닫히며 발생하는 예외는 무시한다.
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

            MozipAiProperties properties = new MozipAiProperties(
                    "http://localhost:" + serverSocket.getLocalPort(), 3, 1);
            ChatResponseClient realClient = new ChatResponseClient(RestClient.builder(), properties);

            long start = System.nanoTime();
            assertThatThrownBy(() -> realClient.respond(sampleRequest()))
                    .isInstanceOf(ResourceAccessException.class);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(elapsed).isLessThan(Duration.ofSeconds(4));
        }
    }

    @Test
    void history의_message와_reply가_camelCase_필드명으로_직렬화된다() {
        ChatResponseRequest requestWithHistory = new ChatResponseRequest(
                "신청 기간은?",
                List.of(),
                null,
                List.of(),
                List.of(new ChatTurn("국민취업지원제도 알려줘", "국민취업지원제도는 취업지원서비스와 소득지원을 결합한 제도입니다."))
        );

        mockServer.expect(requestTo("http://localhost:9999/api/v1/chat/respond"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("\"history\""),
                        Matchers.containsString("\"message\":\"국민취업지원제도 알려줘\""),
                        Matchers.containsString("\"reply\":\"국민취업지원제도는 취업지원서비스와 소득지원을 결합한 제도입니다.\"")
                )))
                .andRespond(withSuccess("""
                        {"reply":"ok"}
                        """, MediaType.APPLICATION_JSON));

        chatResponseClient.respond(requestWithHistory);
    }

    private ChatResponseRequest sampleRequest() {
        return new ChatResponseRequest(
                "국민취업지원제도가 뭐야?",
                List.of(new GroundingPolicy(1L, "국민취업지원제도", EligibilityStatus.ELIGIBLE, LocalDate.of(2026, 12, 31))),
                new PolicyDetailGrounding("국민취업지원제도", "요약", "만 15세 이상 69세 이하 구직자", "상시 신청 가능", "고용노동부"),
                List.of(),
                List.of()
        );
    }
}
