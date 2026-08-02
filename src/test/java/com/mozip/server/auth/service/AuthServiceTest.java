package com.mozip.server.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mozip.server.auth.client.KakaoOAuthClient;
import com.mozip.server.auth.dto.KakaoLoginRequest;
import com.mozip.server.auth.dto.KakaoTokenResponse;
import com.mozip.server.auth.dto.KakaoUserInfoResponse;
import com.mozip.server.auth.dto.RefreshTokenRequest;
import com.mozip.server.auth.dto.TokenResponse;
import com.mozip.server.auth.exception.InvalidRefreshTokenException;
import com.mozip.server.auth.jwt.JwtTokenProvider;
import com.mozip.server.user.entity.OAuthProvider;
import com.mozip.server.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AuthServiceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuthService authService;

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    @Test
    void 처음_로그인하는_카카오_사용자는_새로_생성되고_isNewUser가_true다() {
        given카카오_로그인_응답("auth-code", "kakao-access-token", 11111L, "new@kakao.com");

        TokenResponse response = authService.loginWithKakao(new KakaoLoginRequest("auth-code"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.isNewUser()).isTrue();
        assertThat(userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "11111")).isPresent();
    }

    @Test
    void 이미_가입된_카카오_사용자는_재사용되고_isNewUser가_false다() {
        given카카오_로그인_응답("auth-code", "kakao-access-token", 22222L, "existing@kakao.com");

        TokenResponse firstLogin = authService.loginWithKakao(new KakaoLoginRequest("auth-code"));
        TokenResponse secondLogin = authService.loginWithKakao(new KakaoLoginRequest("auth-code"));

        long count = userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "22222").stream().count();
        assertThat(count).isEqualTo(1);
        assertThat(firstLogin.isNewUser()).isTrue();
        assertThat(secondLogin.isNewUser()).isFalse();
    }

    @Test
    void refresh_토큰으로_재발급하면_기존_토큰은_폐기된다() {
        given카카오_로그인_응답("auth-code", "kakao-access-token", 33333L, "refresh@kakao.com");
        TokenResponse firstTokens = authService.loginWithKakao(new KakaoLoginRequest("auth-code"));

        TokenResponse secondTokens = authService.refresh(new RefreshTokenRequest(firstTokens.refreshToken()));

        assertThat(secondTokens.refreshToken()).isNotEqualTo(firstTokens.refreshToken());
        assertThat(secondTokens.isNewUser()).isFalse();
        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(firstTokens.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void 존재하지_않는_refresh_토큰이면_예외가_발생한다() {
        String unknownRefreshToken = jwtTokenProvider.createRefreshToken("999999");

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(unknownRefreshToken)))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logout하면_refresh_토큰이_폐기되어_재사용할_수_없다() {
        given카카오_로그인_응답("auth-code", "kakao-access-token", 44444L, "logout@kakao.com");
        TokenResponse tokens = authService.loginWithKakao(new KakaoLoginRequest("auth-code"));
        Long userId = userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "44444").orElseThrow().getId();

        authService.logout(userId, new RefreshTokenRequest(tokens.refreshToken()));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(tokens.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void 다른_사용자의_refresh_토큰은_로그아웃으로_폐기되지_않는다() {
        given카카오_로그인_응답("auth-code", "kakao-access-token", 55555L, "owner@kakao.com");
        TokenResponse ownerTokens = authService.loginWithKakao(new KakaoLoginRequest("auth-code"));
        Long attackerId = 999999L;

        authService.logout(attackerId, new RefreshTokenRequest(ownerTokens.refreshToken()));

        TokenResponse refreshed = authService.refresh(new RefreshTokenRequest(ownerTokens.refreshToken()));
        assertThat(refreshed.accessToken()).isNotBlank();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 동일한_refresh_토큰으로_동시에_재발급하면_정확히_하나만_성공한다() throws Exception {
        given카카오_로그인_응답("auth-code", "kakao-access-token", 66666L, "concurrent@kakao.com");
        TokenResponse tokens = authService.loginWithKakao(new KakaoLoginRequest("auth-code"));
        Long userId = userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "66666").orElseThrow().getId();

        try {
            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            List<Future<TokenResponse>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return authService.refresh(new RefreshTokenRequest(tokens.refreshToken()));
                }));
            }

            readyLatch.await();
            startLatch.countDown();

            int successCount = 0;
            int failureCount = 0;
            for (Future<TokenResponse> future : futures) {
                try {
                    future.get(5, TimeUnit.SECONDS);
                    successCount++;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof InvalidRefreshTokenException) {
                        failureCount++;
                    } else {
                        throw new AssertionError("예상치 못한 예외 발생", e.getCause());
                    }
                }
            }
            executor.shutdown();

            assertThat(successCount).isEqualTo(1);
            assertThat(failureCount).isEqualTo(1);
        } finally {
            userRepository.deleteById(userId);
        }
    }

    private void given카카오_로그인_응답(String code, String kakaoAccessToken, Long kakaoUserId, String email) {
        when(kakaoOAuthClient.exchangeToken(code)).thenReturn(new KakaoTokenResponse(kakaoAccessToken));
        when(kakaoOAuthClient.fetchUserInfo(kakaoAccessToken))
                .thenReturn(new KakaoUserInfoResponse(kakaoUserId, new KakaoUserInfoResponse.KakaoAccount(email)));
    }
}
