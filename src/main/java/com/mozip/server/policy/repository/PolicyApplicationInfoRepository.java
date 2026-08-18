package com.mozip.server.policy.repository;

import com.mozip.server.policy.entity.PolicyApplicationInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyApplicationInfoRepository extends JpaRepository<PolicyApplicationInfo, Long> {

    Optional<PolicyApplicationInfo> findByPolicyId(Long policyId);
}
