package com.mozip.server.policy.repository;

import com.mozip.server.policy.entity.PolicyRegion;
import com.mozip.server.policy.entity.PolicyRegionId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyRegionRepository extends JpaRepository<PolicyRegion, PolicyRegionId> {

    @Query("SELECT pr.region.id FROM PolicyRegion pr WHERE pr.policy.id = :policyId")
    List<Long> findRegionIdsByPolicyId(@Param("policyId") Long policyId);

    @Query("SELECT pr FROM PolicyRegion pr JOIN FETCH pr.region WHERE pr.policy.id IN :policyIds")
    List<PolicyRegion> findByPolicyIdIn(@Param("policyIds") List<Long> policyIds);
}
