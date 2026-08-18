package com.mozip.server.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mozip.server.ai.dto.RecommendationReasonRequest;
import com.mozip.server.ai.dto.RecommendationReasonResponse;
import com.mozip.server.recommendation.domain.ConditionStatus;
import com.mozip.server.recommendation.domain.ConditionType;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import java.net.ServerSocket;
import java.time.Duration;
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

class RecommendationReasonClientTest {

    private MockRestServiceServer mockServer;
    private RecommendationReasonClient recommendationReasonClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient mockedRestClient = builder.baseUrl("http://localhost:9999").build();
        recommendationReasonClient = new RecommendationReasonClient(mockedRestClient);
    }

    @Test
    void POST_요청으로_explain_엔드포인트를_호출한다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/recommendations/explain"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"reason":"나이 조건을 충족합니다."}
                        """, MediaType.APPLICATION_JSON));

        RecommendationReasonResponse response = recommendationReasonClient.explain(sampleRequest());

        assertThat(response.reason()).isEqualTo("나이 조건을 충족합니다.");
    }

    @Test
    void request_body가_계약대로_enum_wire_값과_필드명을_담는다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/recommendations/explain"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("\"policyTitle\":\"청년내일채움공제\""),
                        Matchers.containsString("\"eligibilityStatus\":\"NEEDS_REVIEW\""),
                        Matchers.containsString("\"type\":\"AGE\""),
                        Matchers.containsString("\"status\":\"MATCHED\""),
                        Matchers.containsString("\"reason\":\"만 25세는 19~34세 조건을 충족합니다\"")
                )))
                .andRespond(withSuccess("""
                        {"reason":"ok"}
                        """, MediaType.APPLICATION_JSON));

        recommendationReasonClient.explain(sampleRequest());
    }

    @Test
    void 클라이언트_오류_응답이면_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/recommendations/explain"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"VALIDATION_ERROR","message":"invalid","detail":null}
                                """));

        assertThatThrownBy(() -> recommendationReasonClient.explain(sampleRequest()))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void 서버_오류_응답이면_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/recommendations/explain"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> recommendationReasonClient.explain(sampleRequest()))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void malformed_response면_RestClientException_계열_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/recommendations/explain"))
                .andRespond(withSuccess("이건 JSON이 아님", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> recommendationReasonClient.explain(sampleRequest()))
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
            RecommendationReasonClient realClient = new RecommendationReasonClient(RestClient.builder(), properties);

            long start = System.nanoTime();
            assertThatThrownBy(() -> realClient.explain(sampleRequest()))
                    .isInstanceOf(ResourceAccessException.class);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(elapsed).isLessThan(Duration.ofSeconds(4));
        }
    }

    private RecommendationReasonRequest sampleRequest() {
        return new RecommendationReasonRequest(
                "청년내일채움공제",
                EligibilityStatus.NEEDS_REVIEW,
                List.of(new RecommendationReasonRequest.ConditionEntry(
                        ConditionType.AGE, ConditionStatus.MATCHED, "만 25세는 19~34세 조건을 충족합니다"))
        );
    }
}
