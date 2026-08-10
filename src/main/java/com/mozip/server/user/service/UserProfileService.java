package com.mozip.server.user.service;

import com.mozip.server.region.entity.Region;
import com.mozip.server.region.exception.RegionNotFoundException;
import com.mozip.server.region.repository.RegionRepository;
import com.mozip.server.user.dto.UserProfileResponse;
import com.mozip.server.user.dto.UserProfileUpdateRequest;
import com.mozip.server.user.entity.User;
import com.mozip.server.user.entity.UserProfile;
import com.mozip.server.user.exception.UserNotFoundException;
import com.mozip.server.user.exception.UserProfileAlreadyExistsException;
import com.mozip.server.user.exception.UserProfileNotFoundException;
import com.mozip.server.user.exception.UserRegionNotSelectableException;
import com.mozip.server.user.repository.UserProfileRepository;
import com.mozip.server.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;

    public UserProfileService(UserProfileRepository userProfileRepository, UserRepository userRepository,
                               RegionRepository regionRepository) {
        this.userProfileRepository = userProfileRepository;
        this.userRepository = userRepository;
        this.regionRepository = regionRepository;
    }

    public UserProfileResponse getMyProfile(Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));
        return UserProfileResponse.from(profile);
    }

    @Transactional
    public UserProfileResponse upsertMyProfile(Long userId, UserProfileUpdateRequest request) {
        Region region = regionRepository.findById(request.regionId())
                .orElseThrow(() -> new RegionNotFoundException(request.regionId()));
        if (region.getParent() == null) {
            throw new UserRegionNotSelectableException(request.regionId());
        }

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .map(existing -> {
                    existing.update(request.birthDate(), region, request.gender(), request.incomeType(),
                            request.incomeValue(), request.employmentStatus(), request.householdType());
                    return existing;
                })
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new UserNotFoundException(userId));
                    UserProfile created = UserProfile.builder()
                            .user(user)
                            .birthDate(request.birthDate())
                            .region(region)
                            .gender(request.gender())
                            .incomeType(request.incomeType())
                            .incomeValue(request.incomeValue())
                            .employmentStatus(request.employmentStatus())
                            .householdType(request.householdType())
                            .build();
                    try {
                        return userProfileRepository.save(created);
                    } catch (DataIntegrityViolationException e) {
                        throw new UserProfileAlreadyExistsException(userId);
                    }
                });

        return UserProfileResponse.from(profile);
    }
}
