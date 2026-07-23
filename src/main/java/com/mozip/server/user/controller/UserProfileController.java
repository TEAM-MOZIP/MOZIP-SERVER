package com.mozip.server.user.controller;

import com.mozip.server.user.dto.UserProfileResponse;
import com.mozip.server.user.dto.UserProfileUpdateRequest;
import com.mozip.server.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "UserProfile", description = "사용자 프로필 API")
@RestController
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @Operation(summary = "내 프로필 조회")
    @GetMapping("/api/users/me/profile")
    public UserProfileResponse getMyProfile(@AuthenticationPrincipal String userId) {
        return userProfileService.getMyProfile(Long.valueOf(userId));
    }

    @Operation(summary = "내 프로필 등록/전체 수정")
    @PutMapping("/api/users/me/profile")
    public UserProfileResponse upsertMyProfile(@AuthenticationPrincipal String userId,
                                                @RequestBody @Valid UserProfileUpdateRequest request) {
        return userProfileService.upsertMyProfile(Long.valueOf(userId), request);
    }
}
