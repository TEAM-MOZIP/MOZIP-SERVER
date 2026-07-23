package com.mozip.server.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.auth.entity.RefreshToken;
import com.mozip.server.user.entity.OAuthProvider;
import com.mozip.server.user.entity.User;
import com.mozip.server.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
class RefreshTokenRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void tokenHash로_RefreshToken을_조회한다() {
        User user = userRepository.save(User.builder()
                .email("refresh-test@example.com")
                .provider(OAuthProvider.KAKAO)
                .providerUserId("kakao-99999")
                .build());

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash("hashed-token-value")
                .expiresAt(LocalDateTime.now().plusDays(14))
                .build());

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash("hashed-token-value");

        assertThat(found).isPresent();
        assertThat(found.get().getUser().getId()).isEqualTo(user.getId());
        assertThat(found.get().isActive()).isTrue();
    }
}