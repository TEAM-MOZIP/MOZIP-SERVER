package com.mozip.server.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mozip.server.ai.dto.PolicySummaryRequest;
import com.mozip.server.ai.dto.PolicySummaryResponse;
import java.net.ServerSocket;
import java.time.Duration;
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

class PolicySummaryClientTest {

    private MockRestServiceServer mockServer;
    private PolicySummaryClient policySummaryClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient mockedRestClient = builder.baseUrl("http://localhost:9999").build();
        policySummaryClient = new PolicySummaryClient(mockedRestClient);
    }

    @Test
    void POST_요청으로_generate_엔드포인트를_호출한다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/summaries/generate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"summary":"국민취업지원제도는 취업지원서비스와 소득지원을 결합한 제도입니다."}
                        """, MediaType.APPLICATION_JSON));

        PolicySummaryResponse response = policySummaryClient.generate(sampleRequest());

        assertThat(response.summary()).isEqualTo("국민취업지원제도는 취업지원서비스와 소득지원을 결합한 제도입니다.");
    }

    @Test
    void request_body가_계약대로_camelCase_필드명을_담는다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/summaries/generate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("\"title\":\"국민취업지원제도\""),
                        Matchers.containsString("\"targetDescription\":\"만 15세 이상 69세 이하 구직자\""),
                        Matchers.containsString("\"benefitDescription\":\"월 50만원씩 최대 6개월 지급\"")
                )))
                .andRespond(withSuccess("""
                        {"summary":"ok"}
                        """, MediaType.APPLICATION_JSON));

        policySummaryClient.generate(sampleRequest());
    }

    @Test
    void 클라이언트_오류_응답이면_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/summaries/generate"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"VALIDATION_ERROR","message":"invalid","detail":null}
                                """));

        assertThatThrownBy(() -> policySummaryClient.generate(sampleRequest()))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void 서버_오류_응답이면_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/summaries/generate"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> policySummaryClient.generate(sampleRequest()))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void malformed_response면_RestClientException_계열_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/summaries/generate"))
                .andRespond(withSuccess("이건 JSON이 아님", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> policySummaryClient.generate(sampleRequest()))
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
            PolicySummaryClient realClient = new PolicySummaryClient(RestClient.builder(), properties);

            long start = System.nanoTime();
            assertThatThrownBy(() -> realClient.generate(sampleRequest()))
                    .isInstanceOf(ResourceAccessException.class);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(elapsed).isLessThan(Duration.ofSeconds(4));
        }
    }

    private PolicySummaryRequest sampleRequest() {
        return new PolicySummaryRequest("국민취업지원제도", "취업지원서비스와 소득지원을 결합한 제도입니다.",
                "만 15세 이상 69세 이하 구직자", "월 50만원씩 최대 6개월 지급");
    }
}
