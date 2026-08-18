package com.mozip.server.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mozip.server.ai.dto.ApplicationGuideRequest;
import com.mozip.server.ai.dto.ApplicationGuideResponse;
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

class ApplicationGuideClientTest {

    private MockRestServiceServer mockServer;
    private ApplicationGuideClient applicationGuideClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient mockedRestClient = builder.baseUrl("http://localhost:9999").build();
        applicationGuideClient = new ApplicationGuideClient(mockedRestClient);
    }

    @Test
    void POST_요청으로_generate_엔드포인트를_호출한다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/guides/generate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"steps":[{"order":1,"title":"신청","description":"온라인으로 신청합니다."}],"requiredDocuments":["신청서"]}
                        """, MediaType.APPLICATION_JSON));

        ApplicationGuideResponse response = applicationGuideClient.generate(sampleRequest());

        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().get(0).title()).isEqualTo("신청");
        assertThat(response.requiredDocuments()).containsExactly("신청서");
    }

    @Test
    void request_body가_계약대로_camelCase_필드명을_담는다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/guides/generate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("\"applicationInstructions\":\"고용센터 방문 또는 온라인 신청\""),
                        Matchers.containsString("\"requiredDocumentsSource\":\"취업지원신청서\"")
                )))
                .andRespond(withSuccess("""
                        {"steps":[{"order":1,"title":"신청","description":"ok"}],"requiredDocuments":[]}
                        """, MediaType.APPLICATION_JSON));

        applicationGuideClient.generate(sampleRequest());
    }

    @Test
    void 클라이언트_오류_응답이면_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/guides/generate"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"VALIDATION_ERROR","message":"invalid","detail":null}
                                """));

        assertThatThrownBy(() -> applicationGuideClient.generate(sampleRequest()))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void 서버_오류_응답이면_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/guides/generate"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> applicationGuideClient.generate(sampleRequest()))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void malformed_response면_RestClientException_계열_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/guides/generate"))
                .andRespond(withSuccess("이건 JSON이 아님", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> applicationGuideClient.generate(sampleRequest()))
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
            ApplicationGuideClient realClient = new ApplicationGuideClient(RestClient.builder(), properties);

            long start = System.nanoTime();
            assertThatThrownBy(() -> realClient.generate(sampleRequest()))
                    .isInstanceOf(ResourceAccessException.class);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(elapsed).isLessThan(Duration.ofSeconds(4));
        }
    }

    private ApplicationGuideRequest sampleRequest() {
        return new ApplicationGuideRequest("고용센터 방문 또는 온라인 신청", "취업지원신청서");
    }
}
