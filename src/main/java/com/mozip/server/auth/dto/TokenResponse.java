package com.mozip.server.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        boolean isNewUser
) {
}