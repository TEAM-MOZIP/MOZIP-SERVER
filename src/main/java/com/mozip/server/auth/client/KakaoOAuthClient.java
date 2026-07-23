package com.mozip.server.auth.client;

import com.mozip.server.auth.dto.KakaoTokenResponse;
import com.mozip.server.auth.dto.KakaoUserInfoResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@EnableConfigurationProperties(KakaoOAuthProperties.class)
public class KakaoOAuthClient {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final KakaoOAuthProperties properties;

    public KakaoOAuthClient(RestClient.Builder restClientBuilder, KakaoOAuthProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public KakaoTokenResponse exchangeToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", code);

        return restClient.post()
                .uri(TOKEN_URI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KakaoTokenResponse.class);
    }

    public KakaoUserInfoResponse fetchUserInfo(String kakaoAccessToken) {
        return restClient.get()
                .uri(USER_INFO_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                .retrieve()
                .body(KakaoUserInfoResponse.class);
    }
}
