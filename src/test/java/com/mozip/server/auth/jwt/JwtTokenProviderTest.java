package com.mozip.server.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "test-jwt-secret-key-for-unit-test-please-000000";

    private final JwtProperties jwtProperties = new JwtProperties(SECRET, 1800, 1209600);
    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(jwtProperties);

    @Test
    void ACCESS_토큰을_생성하고_파싱하면_subject와_tokenType이_일치한다() {
        String token = jwtTokenProvider.createAccessToken("1");

        Claims claims = jwtTokenProvider.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(jwtTokenProvider.isAccessToken(claims)).isTrue();
    }

    @Test
    void REFRESH_토큰은_isAccessToken이_false다() {
        String token = jwtTokenProvider.createRefreshToken("1");

        Claims claims = jwtTokenProvider.parseClaims(token);

        assertThat(jwtTokenProvider.isAccessToken(claims)).isFalse();
    }

    @Test
    void 만료된_토큰을_파싱하면_ExpiredJwtException이_발생한다() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        Instant past = Instant.now().minusSeconds(10);
        String expiredToken = Jwts.builder()
                .issuer("mozip-server")
                .subject("1")
                .claim("tokenType", TokenType.ACCESS.name())
                .issuedAt(Date.from(past.minusSeconds(10)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> jwtTokenProvider.parseClaims(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void 위조된_토큰을_파싱하면_예외가_발생한다() {
        String token = jwtTokenProvider.createAccessToken("1");
        String tampered = tamper(token);

        assertThatThrownBy(() -> jwtTokenProvider.parseClaims(tampered))
                .isInstanceOf(JwtException.class);
    }

    private String tamper(String token) {
        int index = token.length() / 2;
        char original = token.charAt(index);
        char replacement = original == 'a' ? 'b' : 'a';
        return token.substring(0, index) + replacement + token.substring(index + 1);
    }

    @Test
    void issuer가_다르면_파싱에_실패한다() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        Instant now = Instant.now();
        String otherIssuerToken = Jwts.builder()
                .issuer("other-issuer")
                .subject("1")
                .claim("tokenType", TokenType.ACCESS.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(1800)))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> jwtTokenProvider.parseClaims(otherIssuerToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void REFRESH_토큰으로_parseAccessTokenClaims를_호출하면_InvalidTokenTypeException이_발생한다() {
        String refreshToken = jwtTokenProvider.createRefreshToken("1");

        assertThatThrownBy(() -> jwtTokenProvider.parseAccessTokenClaims(refreshToken))
                .isInstanceOf(InvalidTokenTypeException.class);
    }
}