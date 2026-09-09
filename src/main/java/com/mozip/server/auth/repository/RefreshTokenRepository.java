package com.mozip.server.auth.repository;

import com.mozip.server.auth.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    long countByUserId(Long userId);

    @Modifying
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revokedAt = :now
            WHERE rt.tokenHash = :tokenHash
              AND rt.revokedAt IS NULL
              AND rt.expiresAt > :now
            """)
    int revokeIfActive(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);
}
