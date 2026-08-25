package com.mozip.server.auth.config;

import com.mozip.server.auth.jwt.JwtAuthenticationFilter;
import com.mozip.server.auth.jwt.JwtProperties;
import com.mozip.server.auth.jwt.JwtTokenProvider;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final CorsProperties corsProperties;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider,
                           CustomAuthenticationEntryPoint authenticationEntryPoint,
                           CustomAccessDeniedHandler accessDeniedHandler,
                           CorsProperties corsProperties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/policies/*/terms/explain").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/policies/*/application-guide").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/policies/*/summary").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/chat/messages").authenticated()
                        .requestMatchers("/api/policies/**", "/swagger-ui/**", "/v3/api-docs/**", "/error",
                                "/api/auth/kakao/login", "/api/auth/refresh", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/regions", "/api/categories").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Bearer 토큰 인증이라 쿠키 자격증명이 필요 없어 {@code allowCredentials}는 기본값(false)을 유지한다.
     * {@code corsProperties.allowedOrigins()}가 비어 있으면(local/test 등 CORS_ALLOWED_ORIGINS 미설정 환경)
     * 어떤 origin도 허용하지 않는다 — 그 환경들은 브라우저 cross-origin 호출을 애초에 검증하지 않기 때문에
     * 실질적인 영향이 없다.
     */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}