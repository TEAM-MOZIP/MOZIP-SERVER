package com.mozip.server.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mozip.server.auth.config.CustomAccessDeniedHandler;
import com.mozip.server.auth.config.CustomAuthenticationEntryPoint;
import com.mozip.server.auth.config.SecurityConfig;
import com.mozip.server.auth.dto.KakaoLoginRequest;
import com.mozip.server.auth.dto.RefreshTokenRequest;
import com.mozip.server.auth.dto.TokenResponse;
import com.mozip.server.auth.jwt.JwtTokenProvider;
import com.mozip.server.auth.service.AuthService;
import com.mozip.server.user.dto.UserResponse;
import com.mozip.server.user.entity.OAuthProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtTokenProvider.class})
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AuthService authService;

    @Test
    void 카카오_로그인은_토큰_없이_호출_가능하고_isNewUser를_포함한다() throws Exception {
        when(authService.loginWithKakao(any())).thenReturn(new TokenResponse("access", "refresh", 1800, true));

        mockMvc.perform(post("/api/auth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KakaoLoginRequest("auth-code"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.isNewUser").value(true));
    }

    @Test
    void 토큰_재발급은_토큰_없이_호출_가능하다() throws Exception {
        when(authService.refresh(any())).thenReturn(new TokenResponse("new-access", "new-refresh", 1800, false));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.isNewUser").value(false));
    }

    @Test
    void 로그아웃은_토큰_없이_호출하면_401이다() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("refresh-token"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그아웃은_유효한_토큰이면_204다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("refresh-token"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void 내_정보_조회는_토큰_없이_호출하면_401이다() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 내_정보_조회는_유효한_토큰이면_200이다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken("1");
        when(authService.getMyInfo(eq(1L))).thenReturn(new UserResponse(1L, "test@kakao.com", OAuthProvider.KAKAO));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@kakao.com"));
    }
}
