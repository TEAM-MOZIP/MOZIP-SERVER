package com.mozip.server.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mozip.server.auth.jwt.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityFilterChainTest.TestOnlyController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtTokenProvider.class,
        SecurityFilterChainTest.TestOnlyControllerConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "cors.allowed-origins=https://mozip.vercel.app")
class SecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @RestController
    public static class TestOnlyController {

        @GetMapping("/api/policies/ping")
        public String openEndpoint() {
            return "open";
        }

        @GetMapping("/test/protected")
        public String protectedEndpoint() {
            return "protected";
        }

        @GetMapping("/api/regions")
        public String regions() {
            return "regions";
        }

        @GetMapping("/api/categories")
        public String categories() {
            return "categories";
        }
    }

    @TestConfiguration
    static class TestOnlyControllerConfig {

        @Bean
        public TestOnlyController testOnlyController() {
            return new TestOnlyController();
        }
    }

    @Test
    void permitAll_경로는_토큰_없이_접근_가능하다() throws Exception {
        mockMvc.perform(get("/api/policies/ping"))
                .andExpect(status().isOk());
    }

    @Test
    void 보호_경로는_토큰_없이_접근하면_401이다() throws Exception {
        mockMvc.perform(get("/test/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 지역_목록_조회는_토큰_없이_접근_가능하다() throws Exception {
        mockMvc.perform(get("/api/regions"))
                .andExpect(status().isOk());
    }

    @Test
    void 카테고리_목록_조회는_토큰_없이_접근_가능하다() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void 지역_경로에_대한_POST는_토큰_없이_접근하면_401이다() throws Exception {
        mockMvc.perform(post("/api/regions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 카테고리_경로에_대한_POST는_토큰_없이_접근하면_401이다() throws Exception {
        mockMvc.perform(post("/api/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 보호_경로는_유효한_ACCESS_토큰이면_200이다() throws Exception {
        String token = jwtTokenProvider.createAccessToken("1");

        mockMvc.perform(get("/test/protected").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void 만료된_토큰이면_401이다() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        Instant past = Instant.now().minusSeconds(10);
        String expiredToken = Jwts.builder()
                .issuer("mozip-server")
                .subject("1")
                .claim("tokenType", "ACCESS")
                .issuedAt(Date.from(past.minusSeconds(10)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/test/protected").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 위조된_토큰이면_401이다() throws Exception {
        String token = jwtTokenProvider.createAccessToken("1");
        int index = token.length() / 2;
        char original = token.charAt(index);
        char replacement = original == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, index) + replacement + token.substring(index + 1);

        mockMvc.perform(get("/test/protected").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void REFRESH_토큰으로_인증하면_401이다() throws Exception {
        String refreshToken = jwtTokenProvider.createRefreshToken("1");

        mockMvc.perform(get("/test/protected").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuator_health는_JWT_인증_실패로_차단되지_않는다() throws Exception {
        // 이 슬라이스 테스트에는 실제 actuator 핸들러가 로드되지 않아 200을 검증할 수는 없다(실제 200/503 동작은
        // Docker 통합 검증으로 확인함). 여기서 검증하는 것은 SecurityConfig의 permitAll 매칭 자체이며,
        // /actuator/health가 permitAll에서 빠지면 이 필터 체인이 401로 먼저 막는다.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }

    @Test
    void 허용된_origin의_CORS_preflight_요청은_허용된다() throws Exception {
        mockMvc.perform(options("/api/policies/ping")
                        .header(HttpHeaders.ORIGIN, "https://mozip.vercel.app")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://mozip.vercel.app"));
    }

    @Test
    void 허용되지_않은_origin의_CORS_preflight_요청은_거부된다() throws Exception {
        mockMvc.perform(options("/api/policies/ping")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}