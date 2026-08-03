package com.mozip.server.bookmark.repository;

import com.mozip.server.bookmark.entity.Bookmark;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    @EntityGraph(attributePaths = {"policy", "policy.organization"})
    List<Bookmark> findByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM Bookmark b WHERE b.user.id = :userId AND b.policy.id = :policyId")
    void deleteByUserIdAndPolicyId(@Param("userId") Long userId, @Param("policyId") Long policyId);

    @Query("SELECT b.policy.id FROM Bookmark b WHERE b.user.id = :userId AND b.policy.id IN :policyIds")
    List<Long> findBookmarkedPolicyIds(@Param("userId") Long userId, @Param("policyIds") List<Long> policyIds);

    boolean existsByUserIdAndPolicyId(Long userId, Long policyId);
}
