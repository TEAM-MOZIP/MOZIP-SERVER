package com.mozip.server.auth.service;

import com.mozip.server.auth.client.KakaoOAuthClient;
import com.mozip.server.auth.dto.KakaoLoginRequest;
import com.mozip.server.auth.dto.KakaoTokenResponse;
import com.mozip.server.auth.dto.KakaoUserInfoResponse;
import com.mozip.server.auth.dto.RefreshTokenRequest;
import com.mozip.server.auth.dto.TokenResponse;
import com.mozip.server.auth.entity.RefreshToken;
import com.mozip.server.auth.exception.ExpiredRefreshTokenException;
import com.mozip.server.auth.exception.InvalidKakaoCodeException;
import com.mozip.server.auth.exception.InvalidRefreshTokenException;
import com.mozip.server.auth.jwt.JwtProperties;
import com.mozip.server.auth.jwt.JwtTokenProvider;
import com.mozip.server.auth.repository.RefreshTokenRepository;
import com.mozip.server.user.dto.UserResponse;
import com.mozip.server.user.entity.OAuthProvider;
import com.mozip.server.user.entity.User;
import com.mozip.server.user.exception.UserNotFoundException;
import com.mozip.server.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final KakaoOAuthClient kakaoOAuthClient;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthService(KakaoOAuthClient kakaoOAuthClient, JwtTokenProvider jwtTokenProvider,
                        JwtProperties jwtProperties, UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository) {
        this.kakaoOAuthClient = kakaoOAuthClient;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public TokenResponse loginWithKakao(KakaoLoginRequest request) {
        KakaoUserInfoResponse kakaoUserInfo = authenticateWithKakao(request.code());
        String providerUserId = String.valueOf(kakaoUserInfo.id());
        String email = kakaoUserInfo.kakaoAccount() != null ? kakaoUserInfo.kakaoAccount().email() : null;

        User user = userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, providerUserId)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .provider(OAuthProvider.KAKAO)
                        .providerUserId(providerUserId)
                        .build()));

        return issueTokens(user);
    }

    public TokenResponse refresh(RefreshTokenRequest request) {
        Claims claims = parseRefreshTokenOrThrow(request.refreshToken());
        String tokenHash = hashToken(request.refreshToken());

        int revokedRows = refreshTokenRepository.revokeIfActive(tokenHash, LocalDateTime.now());
        if (revokedRows == 0) {
            throw new InvalidRefreshTokenException();
        }

        Long userId = Long.valueOf(claims.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidRefreshTokenException::new);

        return issueTokens(user);
    }

    public void logout(Long userId, RefreshTokenRequest request) {
        String tokenHash = hashToken(request.refreshToken());
        refreshTokenRepository.findByTokenHash(tokenHash)
                .filter(token -> token.getUser().getId().equals(userId))
                .ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public UserResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return UserResponse.from(user);
    }

    private KakaoUserInfoResponse authenticateWithKakao(String code) {
        try {
            KakaoTokenResponse kakaoToken = kakaoOAuthClient.exchangeToken(code);
            return kakaoOAuthClient.fetchUserInfo(kakaoToken.accessToken());
        } catch (RestClientResponseException e) {
            log.warn("카카오 API가 오류 응답을 반환하여 인증에 실패했습니다. status={}", e.getStatusCode());
            throw new InvalidKakaoCodeException();
        } catch (RestClientException e) {
            log.warn("카카오 API 호출 중 오류가 발생했습니다. exceptionType={}", e.getClass().getSimpleName());
            throw new InvalidKakaoCodeException();
        }
    }

    private Claims parseRefreshTokenOrThrow(String refreshToken) {
        try {
            return jwtTokenProvider.parseRefreshTokenClaims(refreshToken);
        } catch (ExpiredJwtException e) {
            throw new ExpiredRefreshTokenException();
        } catch (RuntimeException e) {
            throw new InvalidRefreshTokenException();
        }
    }

    private TokenResponse issueTokens(User user) {
        String subject = user.getId().toString();
        String accessToken = jwtTokenProvider.createAccessToken(subject);
        String refreshToken = jwtTokenProvider.createRefreshToken(subject);

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(refreshToken))
                .expiresAt(LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenExpireSeconds()))
                .build());

        return new TokenResponse(accessToken, refreshToken, jwtProperties.accessTokenExpireSeconds());
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
