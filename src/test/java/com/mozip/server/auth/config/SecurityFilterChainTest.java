package com.mozip.server.auth.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityFilterChainTest.TestOnlyController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtTokenProvider.class,
        SecurityFilterChainTest.TestOnlyControllerConfig.class})
@ActiveProfiles("test")
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
}