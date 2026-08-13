package com.mozip.server.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mozip.server.ai.dto.MappingAxis;
import com.mozip.server.ai.dto.SemanticMatchPolicyRequest;
import com.mozip.server.ai.dto.SemanticMatchRequest;
import com.mozip.server.ai.dto.SemanticMatchResponse;
import com.mozip.server.ai.dto.SemanticMatchUserRequest;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.Gender;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class SemanticMatchClientTest {

    private MockRestServiceServer mockServer;
    private SemanticMatchClient semanticMatchClient;

    @BeforeEach
    void setUp() {
        // 순수 RestClient.builder()는 Spring Boot의 Jackson 자동 설정(JavaTimeModule 등록,
        // write-dates-as-timestamps=false)을 거치지 않으므로, 운영 환경과 동일한 직렬화
        // 결과를 검증하기 위해 동일하게 설정된 ObjectMapper를 명시적으로 등록한다.
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        RestClient.Builder builder = RestClient.builder()
                .messageConverters(converters -> converters.add(0,
                        new MappingJackson2HttpMessageConverter(objectMapper)));
        mockServer = MockRestServiceServer.bindTo(builder).build();
        // MockRestServiceServer가 심어둔 requestFactory를 건드리지 않기 위해
        // timeout 설정 로직을 거치지 않는 패키지 프라이빗 생성자를 사용한다.
        RestClient mockedRestClient = builder.baseUrl("http://localhost:9999").build();
        semanticMatchClient = new SemanticMatchClient(mockedRestClient);
    }

    @Test
    void POST_요청으로_semantic_match_batch_엔드포인트를_호출한다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/semantic-match/batch"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"results":[]}
                        """, MediaType.APPLICATION_JSON));

        SemanticMatchResponse response = semanticMatchClient.match(sampleRequest());

        assertThat(response.results()).isEmpty();
    }

    @Test
    void request_body가_계약대로_enum_wire_값과_필드명을_담는다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/semantic-match/batch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("\"gender\":\"MALE\""),
                        Matchers.containsString("\"birthDate\":\"1998-05-14\""),
                        Matchers.containsString("\"regionCode\":\"SEOUL_MAPO\""),
                        Matchers.containsString("\"employmentStatus\":\"JOB_SEEKER\""),
                        Matchers.containsString("\"householdType\":\"SINGLE\""),
                        Matchers.containsString("\"incomeType\":\"MEDIAN_PERCENTAGE\""),
                        Matchers.containsString("\"policyId\":101"),
                        Matchers.containsString("\"regionScope\":\"REGIONAL\""),
                        Matchers.containsString("\"regionCodes\":[\"SEOUL_MAPO\"]"),
                        Matchers.containsString("\"genderCondition\":\"MALE\""),
                        Matchers.containsString("\"allowedEmploymentStatuses\":[\"JOB_SEEKER\"]"),
                        Matchers.containsString("\"allowedHouseholdTypes\":[]")
                )))
                .andRespond(withSuccess("""
                        {"results":[]}
                        """, MediaType.APPLICATION_JSON));

        semanticMatchClient.match(sampleRequest());
    }

    @Test
    void semanticScore와_matchedConcepts_inferencePaths를_정상_파싱한다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/semantic-match/batch"))
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {
                              "policyId": 101,
                              "semanticScore": 0.75,
                              "matchedConcepts": [
                                {
                                  "axis": "region",
                                  "userConceptUri": "http://mozip.ai/ontology#SEOUL_MAPO",
                                  "userConceptCode": "SEOUL_MAPO",
                                  "policyConceptUri": "http://mozip.ai/ontology#SEOUL",
                                  "policyConceptCode": "SEOUL"
                                }
                              ],
                              "inferencePaths": [
                                {
                                  "axis": "region",
                                  "fromConceptUri": "http://mozip.ai/ontology#SEOUL_MAPO",
                                  "relations": ["PART_OF"],
                                  "toConceptUri": "http://mozip.ai/ontology#SEOUL"
                                }
                              ]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        SemanticMatchResponse response = semanticMatchClient.match(sampleRequest());

        assertThat(response.results()).hasSize(1);
        var result = response.results().get(0);
        assertThat(result.policyId()).isEqualTo(101L);
        assertThat(result.semanticScore()).isEqualTo(0.75);
        assertThat(result.matchedConcepts()).hasSize(1);
        assertThat(result.matchedConcepts().get(0).axis()).isEqualTo(MappingAxis.REGION);
        assertThat(result.matchedConcepts().get(0).userConceptCode()).isEqualTo("SEOUL_MAPO");
        assertThat(result.inferencePaths()).hasSize(1);
        assertThat(result.inferencePaths().get(0).relations()).containsExactly("PART_OF");
    }

    @Test
    void semanticScore가_null이면_그대로_null로_파싱된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/semantic-match/batch"))
                .andRespond(withSuccess("""
                        {"results":[{"policyId":101,"semanticScore":null,"matchedConcepts":[],"inferencePaths":[]}]}
                        """, MediaType.APPLICATION_JSON));

        SemanticMatchResponse response = semanticMatchClient.match(sampleRequest());

        assertThat(response.results().get(0).semanticScore()).isNull();
    }

    @Test
    void MappingAxis_6개_snake_case_값을_모두_파싱한다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/semantic-match/batch"))
                .andRespond(withSuccess("""
                        {"results":[{"policyId":101,"semanticScore":1.0,"matchedConcepts":[
                          {"axis":"gender","userConceptUri":"u1","userConceptCode":"c1","policyConceptUri":"p1","policyConceptCode":"pc1"},
                          {"axis":"age_group","userConceptUri":"u2","userConceptCode":"c2","policyConceptUri":"p2","policyConceptCode":"pc2"},
                          {"axis":"region","userConceptUri":"u3","userConceptCode":"c3","policyConceptUri":"p3","policyConceptCode":"pc3"},
                          {"axis":"employment_status","userConceptUri":"u4","userConceptCode":"c4","policyConceptUri":"p4","policyConceptCode":"pc4"},
                          {"axis":"household_type","userConceptUri":"u5","userConceptCode":"c5","policyConceptUri":"p5","policyConceptCode":"pc5"},
                          {"axis":"income_type","userConceptUri":"u6","userConceptCode":"c6","policyConceptUri":"p6","policyConceptCode":"pc6"}
                        ],"inferencePaths":[]}]}
                        """, MediaType.APPLICATION_JSON));

        SemanticMatchResponse response = semanticMatchClient.match(sampleRequest());

        List<MappingAxis> axes = response.results().get(0).matchedConcepts().stream()
                .map(concept -> concept.axis())
                .toList();
        assertThat(axes).containsExactly(
                MappingAxis.GENDER, MappingAxis.AGE_GROUP, MappingAxis.REGION,
                MappingAxis.EMPLOYMENT_STATUS, MappingAxis.HOUSEHOLD_TYPE, MappingAxis.INCOME_TYPE
        );
    }

    @Test
    void 클라이언트_오류_응답이면_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/semantic-match/batch"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"VALIDATION_ERROR","message":"invalid","detail":null}
                                """));

        assertThatThrownBy(() -> semanticMatchClient.match(sampleRequest()))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void 서버_오류_응답이면_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/semantic-match/batch"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> semanticMatchClient.match(sampleRequest()))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void malformed_response면_파싱_예외가_전파된다() {
        mockServer.expect(requestTo("http://localhost:9999/api/v1/semantic-match/batch"))
                .andRespond(withSuccess("이건 JSON이 아님", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> semanticMatchClient.match(sampleRequest()))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    @Timeout(5)
    void properties에_설정한_timeout이_실제로_반영된다() throws Exception {
        // 연결은 받아들이되 응답을 절대 주지 않는 소켓을 열어 read timeout을 유발한다.
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
                    "http://localhost:" + serverSocket.getLocalPort(), 1);
            SemanticMatchClient realClient = new SemanticMatchClient(RestClient.builder(), properties);

            long start = System.nanoTime();
            assertThatThrownBy(() -> realClient.match(sampleRequest()))
                    .isInstanceOf(ResourceAccessException.class);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            // timeout-seconds=1로 설정했으므로 넉넉히 4초 이내에는 반드시 실패해야 한다
            // (설정이 반영되지 않아 기본 timeout-없음 상태였다면 훨씬 오래 걸리거나 끝나지 않는다).
            assertThat(elapsed).isLessThan(Duration.ofSeconds(4));
        }
    }

    private SemanticMatchRequest sampleRequest() {
        SemanticMatchUserRequest user = new SemanticMatchUserRequest(
                Gender.MALE, LocalDate.of(1998, 5, 14), "SEOUL_MAPO",
                EmploymentStatus.JOB_SEEKER, HouseholdType.SINGLE, IncomeType.MEDIAN_PERCENTAGE
        );
        SemanticMatchPolicyRequest policy = new SemanticMatchPolicyRequest(
                101L, RegionScope.REGIONAL, List.of("SEOUL_MAPO"),
                19, 34, Gender.MALE, IncomeType.MEDIAN_PERCENTAGE,
                List.of("JOB_SEEKER"), List.of()
        );
        return new SemanticMatchRequest(user, List.of(policy));
    }
}
