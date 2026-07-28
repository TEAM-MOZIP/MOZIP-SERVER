package com.mozip.server.bookmark.repository;

import com.mozip.server.bookmark.entity.Bookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    @EntityGraph(attributePaths = {"policy", "policy.organization"})
    Page<Bookmark> findByUserId(Long userId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Bookmark b WHERE b.user.id = :userId AND b.policy.id = :policyId")
    void deleteByUserIdAndPolicyId(@Param("userId") Long userId, @Param("policyId") Long policyId);
}
