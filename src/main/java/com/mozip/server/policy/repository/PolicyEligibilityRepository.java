package com.mozip.server.policy.repository;

import com.mozip.server.policy.entity.PolicyEligibility;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyEligibilityRepository extends JpaRepository<PolicyEligibility, Long> {

    Optional<PolicyEligibility> findByPolicyId(Long policyId);
}