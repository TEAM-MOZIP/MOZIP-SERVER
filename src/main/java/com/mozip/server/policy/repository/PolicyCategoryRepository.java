package com.mozip.server.policy.repository;

import com.mozip.server.policy.entity.PolicyCategory;
import com.mozip.server.policy.entity.PolicyCategoryId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyCategoryRepository extends JpaRepository<PolicyCategory, PolicyCategoryId> {

    @Query("SELECT pc FROM PolicyCategory pc JOIN FETCH pc.category WHERE pc.policy.id IN :policyIds")
    List<PolicyCategory> findByPolicyIdIn(@Param("policyIds") List<Long> policyIds);
}
