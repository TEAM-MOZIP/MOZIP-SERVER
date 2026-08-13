package com.mozip.server.ai.mapper;

import com.mozip.server.ai.dto.SemanticMatchPolicyRequest;
import com.mozip.server.ai.dto.SemanticMatchUserRequest;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyRegion;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.region.entity.Region;
import com.mozip.server.user.entity.UserProfile;
import java.util.List;
import java.util.Optional;

public class SemanticMatchRequestMapper {

    private SemanticMatchRequestMapper() {
    }

    public static SemanticMatchUserRequest toUserRequest(UserProfile userProfile) {
        Region region = userProfile.getRegion();
        return new SemanticMatchUserRequest(
                userProfile.getGender(),
                userProfile.getBirthDate(),
                region != null ? region.getCode() : null,
                userProfile.getEmploymentStatus(),
                userProfile.getHouseholdType(),
                userProfile.getIncomeType()
        );
    }

    public static SemanticMatchPolicyRequest toPolicyRequest(Policy policy, Optional<PolicyEligibility> eligibility,
                                                               List<PolicyRegion> policyRegions) {
        List<String> regionCodes = policy.getRegionScope() == RegionScope.NATIONAL
                ? List.of()
                : policyRegions.stream()
                        .map(policyRegion -> policyRegion.getRegion().getCode())
                        .toList();

        return new SemanticMatchPolicyRequest(
                policy.getId(),
                policy.getRegionScope(),
                regionCodes,
                eligibility.map(PolicyEligibility::getMinimumAge).orElse(null),
                eligibility.map(PolicyEligibility::getMaximumAge).orElse(null),
                eligibility.map(PolicyEligibility::getGenderCondition).orElse(null),
                eligibility.map(PolicyEligibility::getIncomeType).orElse(null),
                normalizeList(eligibility.map(PolicyEligibility::getAllowedEmploymentStatuses).orElse(null)),
                normalizeList(eligibility.map(PolicyEligibility::getAllowedHouseholdTypes).orElse(null))
        );
    }

    private static List<String> normalizeList(List<String> values) {
        return values != null ? values : List.of();
    }
}
