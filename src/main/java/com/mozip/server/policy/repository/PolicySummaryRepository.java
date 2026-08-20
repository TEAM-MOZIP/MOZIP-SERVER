package com.mozip.server.policy.repository;

import com.mozip.server.policy.entity.PolicySummary;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicySummaryRepository extends JpaRepository<PolicySummary, Long> {

    Optional<PolicySummary> findByPolicyId(Long policyId);
}
