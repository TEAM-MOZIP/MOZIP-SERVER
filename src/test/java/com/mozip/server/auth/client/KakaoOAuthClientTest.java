package com.mozip.server.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mozip.server.auth.dto.KakaoTokenResponse;
import com.mozip.server.auth.dto.KakaoUserInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoOAuthClientTest {

    private MockRestServiceServer mockServer;
    private KakaoOAuthClient kakaoOAuthClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        KakaoOAuthProperties properties = new KakaoOAuthProperties("test-client-id", "http://localhost:3000/callback");
        kakaoOAuthClient = new KakaoOAuthClient(builder, properties);
    }

    @Test
    void 인가코드로_토큰을_폼_형식으로_교환한다() {
        mockServer.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=test-client-id")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=auth-code")))
                .andRespond(withSuccess(
                        """
                        {"token_type":"bearer","access_token":"kakao-access-token","expires_in":21599}
                        """,
                        MediaType.APPLICATION_JSON));

        KakaoTokenResponse response = kakaoOAuthClient.exchangeToken("auth-code");

        assertThat(response.accessToken()).isEqualTo("kakao-access-token");
    }

    @Test
    void 카카오_액세스_토큰으로_사용자_정보를_조회한다() {
        mockServer.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-access-token"))
                .andRespond(withSuccess(
                        """
                        {"id":123456789,"kakao_account":{"email":"test@kakao.com"}}
                        """,
                        MediaType.APPLICATION_JSON));

        KakaoUserInfoResponse response = kakaoOAuthClient.fetchUserInfo("kakao-access-token");

        assertThat(response.id()).isEqualTo(123456789L);
        assertThat(response.kakaoAccount().email()).isEqualTo("test@kakao.com");
    }

    @Test
    void 카카오_사용자_정보에서_nickname과_profile_image_url을_파싱한다() {
        mockServer.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"id":123456789,"kakao_account":{"email":"test@kakao.com","profile":{"nickname":"모집이","profile_image_url":"https://example.com/profile.jpg"}}}
                        """,
                        MediaType.APPLICATION_JSON));

        KakaoUserInfoResponse response = kakaoOAuthClient.fetchUserInfo("kakao-access-token");

        assertThat(response.kakaoAccount().profile().nickname()).isEqualTo("모집이");
        assertThat(response.kakaoAccount().profile().profileImageUrl()).isEqualTo("https://example.com/profile.jpg");
    }

    @Test
    void 이메일_동의를_하지_않으면_email이_null이다() {
        mockServer.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"id":987654321,"kakao_account":{"profile_needs_agreement":false}}
                        """,
                        MediaType.APPLICATION_JSON));

        KakaoUserInfoResponse response = kakaoOAuthClient.fetchUserInfo("kakao-access-token");

        assertThat(response.id()).isEqualTo(987654321L);
        assertThat(response.kakaoAccount().email()).isNull();
    }
}
