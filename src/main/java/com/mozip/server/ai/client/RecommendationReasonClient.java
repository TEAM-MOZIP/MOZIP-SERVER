package com.mozip.server.ai.client;

import com.mozip.server.ai.dto.RecommendationReasonRequest;
import com.mozip.server.ai.dto.RecommendationReasonResponse;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@EnableConfigurationProperties(MozipAiProperties.class)
public class RecommendationReasonClient {

    private static final String EXPLAIN_URI = "/api/v1/recommendations/explain";

    private final RestClient restClient;

    @Autowired
    public RecommendationReasonClient(RestClient.Builder restClientBuilder, MozipAiProperties properties) {
        this(buildRestClient(restClientBuilder, properties));
    }

    /**
     * MockRestServiceServer로 바인딩된 RestClient를 그대로 주입하기 위한 테스트 전용 생성자.
     * 이 생성자를 거치면 timeout용 requestFactory 재설정이 Mock의 requestFactory를
     * 덮어쓰지 않는다.
     */
    RecommendationReasonClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(RestClient.Builder restClientBuilder, MozipAiProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(properties.explainTimeoutSeconds()))
                .withReadTimeout(Duration.ofSeconds(properties.explainTimeoutSeconds()));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        return restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public RecommendationReasonResponse explain(RecommendationReasonRequest request) {
        return restClient.post()
                .uri(EXPLAIN_URI)
                .body(request)
                .retrieve()
                .body(RecommendationReasonResponse.class);
    }
}
