package com.mozip.server.auth.service;

import com.mozip.server.auth.client.KakaoOAuthClient;
import com.mozip.server.auth.dto.KakaoLoginRequest;
import com.mozip.server.auth.dto.KakaoTokenResponse;
import com.mozip.server.auth.dto.KakaoUserInfoResponse;
import com.mozip.server.auth.dto.RefreshTokenRequest;
import com.mozip.server.auth.dto.TokenResponse;
import com.mozip.server.auth.entity.RefreshToken;
import com.mozip.server.auth.exception.DuplicateKakaoEmailException;
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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
        KakaoUserInfoResponse.KakaoAccount kakaoAccount = kakaoUserInfo.kakaoAccount();
        String email = kakaoAccount != null ? kakaoAccount.email() : null;
        KakaoUserInfoResponse.KakaoAccount.Profile profile = kakaoAccount != null ? kakaoAccount.profile() : null;
        String nickname = profile != null ? profile.nickname() : null;
        String profileImageUrl = profile != null ? profile.profileImageUrl() : null;

        Optional<User> existingUser = userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, providerUserId);
        boolean isNewUser = existingUser.isEmpty();
        User user = existingUser.orElseGet(() -> createKakaoUserOrThrow(providerUserId, email, nickname, profileImageUrl));

        if (!isNewUser) {
            syncKakaoProfileOrThrow(user, email, nickname, profileImageUrl);
        }

        return issueTokens(user, isNewUser);
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

        return issueTokens(user, false);
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

    /**
     * User는 GenerationType.IDENTITY라 save()가 즉시 INSERT를 실행한다(ID를 바로 받아야 하므로
     * 지연 배치가 불가능) — 그래서 syncKakaoProfileOrThrow()와 달리 여기서는 별도 flush() 없이도
     * save() 호출 자체에서 email unique 충돌을 동기적으로 감지할 수 있다.
     */
    private User createKakaoUserOrThrow(String providerUserId, String email, String nickname, String profileImageUrl) {
        try {
            return userRepository.save(User.builder()
                    .email(email)
                    .provider(OAuthProvider.KAKAO)
                    .providerUserId(providerUserId)
                    .nickname(nickname)
                    .profileImageUrl(profileImageUrl)
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.warn("카카오 신규 가입 중 계정 생성이 email unique 제약 위반으로 실패했습니다. "
                    + "provider={}, providerUserId={}", OAuthProvider.KAKAO, providerUserId);
            throw new DuplicateKakaoEmailException();
        }
    }

    /**
     * User.syncKakaoProfile()은 dirty checking으로 UPDATE를 지연시키므로, 여기서 즉시 flush해
     * email unique 충돌을 이 시점에 직접 감지한다. 그렇게 하지 않으면 이후 issueTokens()의
     * refreshToken 저장(INSERT)이나 트랜잭션 commit 시점에 지연 발생해 이 catch를 우회하게 된다.
     * flush 실패 시 해당 DB 트랜잭션은 더 이상 사용할 수 없으므로, 여기서 즉시 전용 예외로 변환해
     * 던지고 이후 어떤 추가 쿼리도 시도하지 않는다 — @Transactional 경계에서 전체 롤백된다.
     */
    private void syncKakaoProfileOrThrow(User user, String email, String nickname, String profileImageUrl) {
        user.syncKakaoProfile(email, nickname, profileImageUrl);
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            log.warn("카카오 재로그인 중 계정 정보 동기화가 email unique 제약 위반으로 실패했습니다. "
                    + "provider={}, providerUserId={}", user.getProvider(), user.getProviderUserId());
            throw new DuplicateKakaoEmailException();
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

    private TokenResponse issueTokens(User user, boolean isNewUser) {
        String subject = user.getId().toString();
        String accessToken = jwtTokenProvider.createAccessToken(subject);
        String refreshToken = jwtTokenProvider.createRefreshToken(subject);

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(refreshToken))
                .expiresAt(LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenExpireSeconds()))
                .build());

        return new TokenResponse(accessToken, refreshToken, jwtProperties.accessTokenExpireSeconds(), isNewUser);
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
