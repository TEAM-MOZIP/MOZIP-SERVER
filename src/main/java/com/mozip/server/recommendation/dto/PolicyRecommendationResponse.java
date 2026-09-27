package com.mozip.server.recommendation.dto;

import com.mozip.server.policy.domain.PolicyAvailabilityResult;
import com.mozip.server.policy.dto.CategoryResponse;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Category;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.recommendation.domain.PolicyEligibilityResult;
import com.mozip.server.region.dto.RegionResponse;
import com.mozip.server.region.entity.Region;
import java.time.LocalDate;
import java.util.List;

public record PolicyRecommendationResponse(
        Long policyId,
        String title,
        String organizationName,
        ApplicationType applicationType,
        LocalDate applicationStartDate,
        LocalDate applicationEndDate,
        PolicyEvaluationResponse.EligibilityResponse eligibility,
        PolicyEvaluationResponse.AvailabilityResponse availability,
        boolean bookmarked,
        Double semanticScore,
        List<CategoryResponse> categories,
        RegionScope regionScope,
        List<RegionResponse> regions,
        Integer minimumAge,
        Integer maximumAge
) {

    /**
     * @param regions 정책에 연결된 지역(policy_regions). 전국 정책(regionScope=NATIONAL)은 비어 있다.
     */
    public PolicyRecommendationResponse {
        categories = categories != null ? List.copyOf(categories) : List.of();
        regions = regions != null ? List.copyOf(regions) : List.of();
    }

    /** 카테고리·지역 없이 만드는 기존 생성자(테스트 등 하위 호환용). */
    public PolicyRecommendationResponse(Long policyId, String title, String organizationName,
                                        ApplicationType applicationType, LocalDate applicationStartDate,
                                        LocalDate applicationEndDate,
                                        PolicyEvaluationResponse.EligibilityResponse eligibility,
                                        PolicyEvaluationResponse.AvailabilityResponse availability,
                                        boolean bookmarked, Double semanticScore) {
        this(policyId, title, organizationName, applicationType, applicationStartDate, applicationEndDate,
                eligibility, availability, bookmarked, semanticScore, List.of(), null, List.of(), null, null);
    }

    public static PolicyRecommendationResponse from(Policy policy, PolicyEligibilityResult eligibilityResult,
                                                      PolicyAvailabilityResult availabilityResult, boolean bookmarked,
                                                      Double semanticScore, List<Category> categories,
                                                      List<Region> regions, PolicyEligibility eligibility) {
        return new PolicyRecommendationResponse(
                policy.getId(),
                policy.getTitle(),
                policy.getOrganization().getName(),
                policy.getApplicationType(),
                policy.getApplicationStartDate(),
                policy.getApplicationEndDate(),
                PolicyEvaluationResponse.EligibilityResponse.from(eligibilityResult),
                PolicyEvaluationResponse.AvailabilityResponse.from(availabilityResult),
                bookmarked,
                semanticScore,
                categories.stream().map(CategoryResponse::from).toList(),
                policy.getRegionScope(),
                regions.stream().map(RegionResponse::from).toList(),
                eligibility != null ? eligibility.getMinimumAge() : null,
                eligibility != null ? eligibility.getMaximumAge() : null
        );
    }
}
