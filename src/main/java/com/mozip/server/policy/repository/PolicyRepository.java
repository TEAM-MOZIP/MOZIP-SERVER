package com.mozip.server.policy.repository;

import com.mozip.server.policy.entity.Policy;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyRepository extends JpaRepository<Policy, Long>, JpaSpecificationExecutor<Policy> {

    @Override
    @EntityGraph(attributePaths = "organization")
    Page<Policy> findAll(Specification<Policy> spec, Pageable pageable);

    @EntityGraph(attributePaths = "organization")
    @Query("SELECT p FROM Policy p WHERE p.id = :id")
    Optional<Policy> findWithOrganizationById(@Param("id") Long id);
}
