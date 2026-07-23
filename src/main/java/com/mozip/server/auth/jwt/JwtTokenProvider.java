package com.mozip.server.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ISSUER = "mozip-server";

    private final SecretKey key;
    private final long accessTokenExpireSeconds;
    private final long refreshTokenExpireSeconds;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpireSeconds = jwtProperties.accessTokenExpireSeconds();
        this.refreshTokenExpireSeconds = jwtProperties.refreshTokenExpireSeconds();
    }

    public String createAccessToken(String subject) {
        return createToken(subject, TokenType.ACCESS, accessTokenExpireSeconds);
    }

    public String createRefreshToken(String subject) {
        return createToken(subject, TokenType.REFRESH, refreshTokenExpireSeconds);
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Claims parseAccessTokenClaims(String token) {
        Claims claims = parseClaims(token);
        if (!isAccessToken(claims)) {
            throw new InvalidTokenTypeException("ACCESS 토큰이 아닙니다. tokenType=" + claims.get(TOKEN_TYPE_CLAIM));
        }
        return claims;
    }

    public Claims parseRefreshTokenClaims(String token) {
        Claims claims = parseClaims(token);
        if (isAccessToken(claims)) {
            throw new InvalidTokenTypeException("REFRESH 토큰이 아닙니다. tokenType=" + claims.get(TOKEN_TYPE_CLAIM));
        }
        return claims;
    }

    public boolean isAccessToken(Claims claims) {
        return TokenType.ACCESS.name().equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    private String createToken(String subject, TokenType tokenType, long expireSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .subject(subject)
                .claim(TOKEN_TYPE_CLAIM, tokenType.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(key)
                .compact();
    }
}