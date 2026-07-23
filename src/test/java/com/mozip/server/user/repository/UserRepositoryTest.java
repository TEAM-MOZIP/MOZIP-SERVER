package com.mozip.server.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.user.entity.OAuthProvider;
import com.mozip.server.user.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void provider와_providerUserId로_사용자를_조회한다() {
        User user = User.builder()
                .email("test-user@example.com")
                .provider(OAuthProvider.KAKAO)
                .providerUserId("kakao-12345")
                .build();
        userRepository.save(user);

        Optional<User> found = userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "kakao-12345");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test-user@example.com");
    }

    @Test
    void 존재하지_않는_provider_조합이면_빈_값을_반환한다() {
        Optional<User> found = userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "unknown-id");

        assertThat(found).isEmpty();
    }
}
