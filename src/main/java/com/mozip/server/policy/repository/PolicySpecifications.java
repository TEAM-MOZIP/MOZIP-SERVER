package com.mozip.server.policy.repository;

import com.mozip.server.policy.domain.AgeGroup;
import com.mozip.server.policy.domain.AvailabilityFilter;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyCategory;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyRegion;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.evaluator.PolicyAvailabilityEvaluator;
import com.mozip.server.region.entity.Region;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
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
            Subquery<Long> parentIdSubquery = query.subquery(Long.class);
            Root<Region> regionRoot = parentIdSubquery.from(Region.class);
            parentIdSubquery.select(regionRoot.get("parent").get("id"))
                    .where(cb.equal(regionRoot.get("id"), regionId));

            Subquery<Long> subquery = query.subquery(Long.class);
            Root<PolicyRegion> policyRegion = subquery.from(PolicyRegion.class);
            subquery.select(policyRegion.get("policy").get("id"))
                    .where(cb.or(
                            cb.equal(policyRegion.get("region").get("id"), regionId),
                            cb.equal(policyRegion.get("region").get("id"), parentIdSubquery)
                    ));
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

    /**
     * {@code PolicyAvailabilityEvaluator}와 같은 규칙으로 신청 가능 상태를 거른다.
     * CLOSED·DRAFT·SUSPENDED는 항상 제외되고, 신청기간이 없거나 뒤집힌 정책(판정 보류)은 어느 상태에도 속하지 않는다.
     */
    public static Specification<Policy> hasAvailability(AvailabilityFilter filter, LocalDate today) {
        if (filter == null) {
            return null;
        }
        return (root, query, cb) -> {
            Path<PolicyStatus> status = root.get("status");
            Path<ApplicationType> applicationType = root.get("applicationType");
            Path<LocalDate> startDate = root.get("applicationStartDate");
            Path<LocalDate> endDate = root.get("applicationEndDate");

            Predicate openStatus = cb.equal(status, PolicyStatus.OPEN);
            Predicate validPeriod = cb.and(
                    cb.equal(applicationType, ApplicationType.PERIOD),
                    cb.isNotNull(startDate),
                    cb.isNotNull(endDate),
                    cb.lessThanOrEqualTo(startDate, endDate));
            Predicate withinPeriod = cb.and(validPeriod,
                    cb.lessThanOrEqualTo(startDate, today),
                    cb.greaterThanOrEqualTo(endDate, today));

            return switch (filter) {
                case OPEN -> cb.or(
                        cb.equal(status, PolicyStatus.ALWAYS_OPEN),
                        cb.and(openStatus, cb.equal(applicationType, ApplicationType.ALWAYS)),
                        cb.and(openStatus, withinPeriod));
                case CLOSING_SOON -> cb.and(openStatus, withinPeriod,
                        cb.lessThanOrEqualTo(endDate,
                                today.plusDays(PolicyAvailabilityEvaluator.CLOSING_SOON_THRESHOLD_DAYS)));
                case UPCOMING -> cb.and(openStatus, validPeriod, cb.greaterThan(startDate, today));
            };
        };
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