package com.mozip.server.user.dto;

import com.mozip.server.user.entity.OAuthProvider;
import com.mozip.server.user.entity.User;

public record UserResponse(
        Long id,
        String email,
        OAuthProvider provider
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getProvider());
    }
}