package com.mozip.server.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserInfoResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(
            @JsonProperty("email") String email,
            @JsonProperty("profile") Profile profile
    ) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Profile(
                @JsonProperty("nickname") String nickname,
                @JsonProperty("profile_image_url") String profileImageUrl
        ) {
        }
    }
}