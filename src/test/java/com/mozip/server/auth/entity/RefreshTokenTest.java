package com.mozip.server.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    @Test
    void revokedAt이_null이면_활성_토큰이다() {
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash("hash")
                .expiresAt(LocalDateTime.now().plusDays(14))
                .build();

        assertThat(refreshToken.isActive()).isTrue();
    }

    @Test
    void revoke_호출_후에는_비활성_토큰이다() {
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash("hash")
                .expiresAt(LocalDateTime.now().plusDays(14))
                .build();

        refreshToken.revoke();

        assertThat(refreshToken.isActive()).isFalse();
        assertThat(refreshToken.getRevokedAt()).isNotNull();
    }

    @Test
    void 폐기되지_않았어도_만료됐으면_비활성_토큰이다() {
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash("hash")
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .build();

        assertThat(refreshToken.getRevokedAt()).isNull();
        assertThat(refreshToken.isActive()).isFalse();
    }
}