package com.mozip.server.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mozip.server.region.exception.RegionNotFoundException;
import com.mozip.server.region.repository.RegionRepository;
import com.mozip.server.user.dto.UserProfileResponse;
import com.mozip.server.user.dto.UserProfileUpdateRequest;
import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;
import com.mozip.server.user.entity.OAuthProvider;
import com.mozip.server.user.entity.User;
import com.mozip.server.user.exception.UserProfileAlreadyExistsException;
import com.mozip.server.user.exception.UserProfileNotFoundException;
import com.mozip.server.user.repository.UserProfileRepository;
import com.mozip.server.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class UserProfileServiceTest {

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Test
    void 프로필이_없으면_최초_등록된다() {
        User user = createUser("profile-new@example.com", "profile-new-1");
        Long regionId = regionRepository.findAll().stream().findFirst().orElseThrow().getId();
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                LocalDate.of(1998, 5, 14), regionId, "F", IncomeType.MEDIAN_PERCENTAGE, 80,
                EmploymentStatus.JOB_SEEKER, HouseholdType.SINGLE
        );

        UserProfileResponse response = userProfileService.upsertMyProfile(user.getId(), request);

        assertThat(response.incomeValue()).isEqualTo(80);
        assertThat(userProfileService.getMyProfile(user.getId()).incomeValue()).isEqualTo(80);
    }

    @Test
    void 이미_프로필이_있으면_수정된다() {
        User user = createUser("profile-update@example.com", "profile-update-1");
        Long regionId = regionRepository.findAll().stream().findFirst().orElseThrow().getId();
        userProfileService.upsertMyProfile(user.getId(), new UserProfileUpdateRequest(
                LocalDate.of(1998, 5, 14), regionId, "F", IncomeType.MEDIAN_PERCENTAGE, 80,
                EmploymentStatus.JOB_SEEKER, HouseholdType.SINGLE
        ));

        UserProfileResponse response = userProfileService.upsertMyProfile(user.getId(), new UserProfileUpdateRequest(
                LocalDate.of(1998, 5, 14), regionId, "F", IncomeType.ABSOLUTE, 120,
                EmploymentStatus.EMPLOYED, HouseholdType.SINGLE
        ));

        assertThat(response.incomeValue()).isEqualTo(120);
        assertThat(response.incomeType()).isEqualTo(IncomeType.ABSOLUTE);
        assertThat(response.employmentStatus()).isEqualTo(EmploymentStatus.EMPLOYED);
    }

    @Test
    void 존재하지_않는_프로필을_조회하면_예외가_발생한다() {
        User user = createUser("profile-none@example.com", "profile-none-1");

        assertThatThrownBy(() -> userProfileService.getMyProfile(user.getId()))
                .isInstanceOf(UserProfileNotFoundException.class);
    }

    @Test
    void 존재하지_않는_지역이면_예외가_발생한다() {
        User user = createUser("profile-badregion@example.com", "profile-badregion-1");
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                LocalDate.of(1998, 5, 14), 999999L, "F", IncomeType.MEDIAN_PERCENTAGE, 80,
                EmploymentStatus.JOB_SEEKER, HouseholdType.SINGLE
        );

        assertThatThrownBy(() -> userProfileService.upsertMyProfile(user.getId(), request))
                .isInstanceOf(RegionNotFoundException.class);
    }

    @Test
    void 서로_다른_사용자의_프로필은_독립적으로_관리된다() {
        User userA = createUser("profile-a@example.com", "profile-a-1");
        User userB = createUser("profile-b@example.com", "profile-b-1");
        Long regionId = regionRepository.findAll().stream().findFirst().orElseThrow().getId();

        userProfileService.upsertMyProfile(userA.getId(), new UserProfileUpdateRequest(
                LocalDate.of(1998, 5, 14), regionId, "F", IncomeType.MEDIAN_PERCENTAGE, 80,
                EmploymentStatus.JOB_SEEKER, HouseholdType.SINGLE
        ));

        assertThatThrownBy(() -> userProfileService.getMyProfile(userB.getId()))
                .isInstanceOf(UserProfileNotFoundException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 동일_사용자가_동시에_최초_프로필을_등록하면_하나만_성공한다() throws Exception {
        User user = createUser("profile-concurrent@example.com", "profile-concurrent-1");
        Long regionId = regionRepository.findAll().stream().findFirst().orElseThrow().getId();
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                LocalDate.of(1998, 5, 14), regionId, "F", IncomeType.MEDIAN_PERCENTAGE, 80,
                EmploymentStatus.JOB_SEEKER, HouseholdType.SINGLE
        );

        try {
            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            List<Future<UserProfileResponse>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return userProfileService.upsertMyProfile(user.getId(), request);
                }));
            }

            readyLatch.await();
            startLatch.countDown();

            int successCount = 0;
            int conflictCount = 0;
            for (Future<UserProfileResponse> future : futures) {
                try {
                    future.get(5, TimeUnit.SECONDS);
                    successCount++;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof UserProfileAlreadyExistsException) {
                        conflictCount++;
                    } else {
                        throw new AssertionError("예상치 못한 예외 발생", e.getCause());
                    }
                }
            }
            executor.shutdown();

            assertThat(successCount).isEqualTo(1);
            assertThat(conflictCount).isEqualTo(1);
            assertThat(userProfileRepository.findByUserId(user.getId())).isPresent();
        } finally {
            userRepository.deleteById(user.getId());
        }
    }

    private User createUser(String email, String providerUserId) {
        return userRepository.save(User.builder()
                .email(email)
                .provider(OAuthProvider.KAKAO)
                .providerUserId(providerUserId)
                .build());
    }
}
