package com.mozip.server.policy.repository;

import com.mozip.server.policy.domain.AgeGroup;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyCategory;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyRegion;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

public class PolicySpecifications {

    private PolicySpecifications() {
    }

    public static Specification<Policy> keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + keyword + "%";
        return (root, query, cb) -> cb.or(
                cb.like(root.get("title"), pattern),
                cb.like(root.get("summary"), pattern)
        );
    }

    public static Specification<Policy> hasCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<PolicyCategory> policyCategory = subquery.from(PolicyCategory.class);
            subquery.select(policyCategory.get("policy").get("id"))
                    .where(cb.equal(policyCategory.get("category").get("id"), categoryId));
            return root.get("id").in(subquery);
        };
    }

    public static Specification<Policy> availableInRegion(Long regionId) {
        if (regionId == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<PolicyRegion> policyRegion = subquery.from(PolicyRegion.class);
            subquery.select(policyRegion.get("policy").get("id"))
                    .where(cb.equal(policyRegion.get("region").get("id"), regionId));
            return cb.or(
                    cb.equal(root.get("regionScope"), RegionScope.NATIONAL),
                    root.get("id").in(subquery)
            );
        };
    }

    public static Specification<Policy> hasStatus(PolicyStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Policy> hasAgeGroup(AgeGroup ageGroup) {
        if (ageGroup == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> nonOverlapping = query.subquery(Long.class);
            Root<PolicyEligibility> eligibility = nonOverlapping.from(PolicyEligibility.class);

            Predicate belowRange = ageGroup.getMinAge() == null
                    ? cb.disjunction()
                    : cb.and(cb.isNotNull(eligibility.get("maximumAge")),
                            cb.lessThan(eligibility.get("maximumAge"), ageGroup.getMinAge()));
            Predicate aboveRange = ageGroup.getMaxAge() == null
                    ? cb.disjunction()
                    : cb.and(cb.isNotNull(eligibility.get("minimumAge")),
                            cb.greaterThan(eligibility.get("minimumAge"), ageGroup.getMaxAge()));

            nonOverlapping.select(eligibility.get("policy").get("id"))
                    .where(cb.or(belowRange, aboveRange));
            return cb.not(root.get("id").in(nonOverlapping));
        };
    }
}