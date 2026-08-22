package com.mozip.server.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mozip.server.ai.dto.ConditionAxis;
import com.mozip.server.ai.dto.ConditionExtractionRequest;
import com.mozip.server.ai.dto.ConditionExtractionResponse;
import com.mozip.server.user.entity.EmploymentStatus;
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

class ConditionExtractionClientTest {

    private MockRestServiceServer mockServer;
    private ConditionExtractionClient conditionExtractionClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient mockedRestClient = builder.baseUrl("http://localhost:9999").build();
        conditionExtractionClient = new ConditionExtractionClient(mockedRestClient);
    }

    @Test
    void POST_요청으로_extract_엔드포인트를_호출한다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/conditions/extract"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"gender":null,"age":25,"regionCode":null,"employmentStatus":null,
                         "householdType":null,"incomeType":null,"incomeValue":null,"unresolvedConditions":[]}
                        """, MediaType.APPLICATION_JSON));

        ConditionExtractionResponse response = conditionExtractionClient.extract(sampleRequest());

        assertThat(response.age()).isEqualTo(25);
    }

    @Test
    void request_body가_계약대로_freeText_필드명을_담는다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/conditions/extract"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(Matchers.containsString("\"freeText\":\"서울 사는 취준생이 받을 정책 있어?\"")))
                .andRespond(withSuccess("""
                        {"unresolvedConditions":[]}
                        """, MediaType.APPLICATION_JSON));

        conditionExtractionClient.extract(sampleRequest());
    }

    @Test
    void unresolvedConditions의_axis가_snake_case_문자열에서_enum으로_역직렬화된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/conditions/extract"))
                .andRespond(withSuccess("""
                        {"unresolvedConditions":[{"axis":"employment_status","rawText":"프리랜서"}]}
                        """, MediaType.APPLICATION_JSON));

        ConditionExtractionResponse response = conditionExtractionClient.extract(sampleRequest());

        assertThat(response.unresolvedConditions()).hasSize(1);
        assertThat(response.unresolvedConditions().get(0).axis()).isEqualTo(ConditionAxis.EMPLOYMENT_STATUS);
        assertThat(response.unresolvedConditions().get(0).rawText()).isEqualTo("프리랜서");
    }

    @Test
    void 응답의_employmentStatus_enum이_정상_역직렬화된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/conditions/extract"))
                .andRespond(withSuccess("""
                        {"employmentStatus":"JOB_SEEKER","unresolvedConditions":[]}
                        """, MediaType.APPLICATION_JSON));

        ConditionExtractionResponse response = conditionExtractionClient.extract(sampleRequest());

        assertThat(response.employmentStatus()).isEqualTo(EmploymentStatus.JOB_SEEKER);
    }

    @Test
    void 클라이언트_오류_응답이면_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/conditions/extract"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"VALIDATION_ERROR","message":"invalid","detail":null}
                                """));

        assertThatThrownBy(() -> conditionExtractionClient.extract(sampleRequest()))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void 서버_오류_응답이면_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/conditions/extract"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> conditionExtractionClient.extract(sampleRequest()))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void malformed_response면_RestClientException_계열_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/conditions/extract"))
                .andRespond(withSuccess("이건 JSON이 아님", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> conditionExtractionClient.extract(sampleRequest()))
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
            ConditionExtractionClient realClient = new ConditionExtractionClient(RestClient.builder(), properties);

            long start = System.nanoTime();
            assertThatThrownBy(() -> realClient.extract(sampleRequest()))
                    .isInstanceOf(ResourceAccessException.class);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(elapsed).isLessThan(Duration.ofSeconds(4));
        }
    }

    private ConditionExtractionRequest sampleRequest() {
        return new ConditionExtractionRequest("서울 사는 취준생이 받을 정책 있어?");
    }
}
