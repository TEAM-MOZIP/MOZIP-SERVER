package com.mozip.server.user.repository;

import com.mozip.server.user.entity.OAuthProvider;
import com.mozip.server.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}