package com.mozip.server.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.region.entity.Region;
import com.mozip.server.region.repository.RegionRepository;
import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;
import com.mozip.server.user.entity.OAuthProvider;
import com.mozip.server.user.entity.User;
import com.mozip.server.user.entity.UserProfile;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
class UserProfileRepositoryTest {

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Test
    void userId로_프로필을_저장하고_조회한다() {
        User user = userRepository.save(User.builder()
                .email("profile-test@example.com")
                .provider(OAuthProvider.KAKAO)
                .providerUserId("profile-test-1")
                .build());
        Region region = regionRepository.findAll().stream().findFirst().orElseThrow();

        userProfileRepository.save(UserProfile.builder()
                .user(user)
                .birthDate(LocalDate.of(1998, 5, 14))
                .region(region)
                .gender("F")
                .incomeType(IncomeType.MEDIAN_PERCENTAGE)
                .incomeValue(80)
                .employmentStatus(EmploymentStatus.JOB_SEEKER)
                .householdType(HouseholdType.SINGLE)
                .build());

        Optional<UserProfile> found = userProfileRepository.findByUserId(user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getIncomeValue()).isEqualTo(80);
        assertThat(found.get().getRegion().getId()).isEqualTo(region.getId());
    }

    @Test
    void 존재하지_않는_userId면_빈_값을_반환한다() {
        Optional<UserProfile> found = userProfileRepository.findByUserId(999999L);

        assertThat(found).isEmpty();
    }
}
