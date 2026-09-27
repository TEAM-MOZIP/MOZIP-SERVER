package com.mozip.server.policy.repository;

import com.mozip.server.policy.entity.PolicyApplicationGuide;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyApplicationGuideRepository extends JpaRepository<PolicyApplicationGuide, Long> {

    Optional<PolicyApplicationGuide> findByPolicyId(Long policyId);
}
