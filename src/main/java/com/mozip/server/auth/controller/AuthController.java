package com.mozip.server.auth.controller;

import com.mozip.server.auth.dto.KakaoLoginRequest;
import com.mozip.server.auth.dto.RefreshTokenRequest;
import com.mozip.server.auth.dto.TokenResponse;
import com.mozip.server.auth.service.AuthService;
import com.mozip.server.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "카카오 로그인", description = "인가 코드로 카카오 로그인을 처리하고 액세스/리프레시 토큰을 발급한다.")
    @PostMapping("/api/auth/kakao/login")
    public TokenResponse kakaoLogin(@RequestBody @Valid KakaoLoginRequest request) {
        return authService.loginWithKakao(request);
    }

    @Operation(summary = "토큰 재발급", description = "리프레시 토큰을 검증하고 새 액세스/리프레시 토큰을 발급한다.")
    @PostMapping("/api/auth/refresh")
    public TokenResponse refresh(@RequestBody @Valid RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @Operation(summary = "로그아웃", description = "요청자 본인 소유의 리프레시 토큰을 폐기한다.")
    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal String userId, @RequestBody @Valid RefreshTokenRequest request) {
        authService.logout(Long.valueOf(userId), request);
    }

    @Operation(summary = "내 정보 조회", description = "인증된 사용자 본인의 정보를 조회한다.")
    @GetMapping("/api/users/me")
    public UserResponse getMyInfo(@AuthenticationPrincipal String userId) {
        return authService.getMyInfo(Long.valueOf(userId));
    }
}
