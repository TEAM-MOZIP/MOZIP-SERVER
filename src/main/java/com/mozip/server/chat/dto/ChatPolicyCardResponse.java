package com.mozip.server.chat.dto;

import com.mozip.server.policy.dto.CategoryResponse;
import com.mozip.server.policy.dto.PolicyAvailabilityResponse;
import com.mozip.server.policy.dto.PolicySummaryResponse;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.region.dto.RegionResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * 챗봇 정책 카드. 정책 목록 카드와 같은 칩(접수 상태·카테고리·지역·연령)에 AI가 쓴 추천 이유와 핵심 정보를 더한다.
 *
 * @param reason            AI가 쓴 추천 이유 한 줄
 * @param highlight         금액·기간 같은 핵심 정보 한 가지(없으면 null)
 * @param eligibilityStatus 사용자가 말한 조건으로 판정한 자격(조건을 말하지 않았으면 null)
 */
public record ChatPolicyCardResponse(
        Long policyId,
        String title,
        String summary,
        String reason,
        String highlight,
        EligibilityStatus eligibilityStatus,
        ApplicationType applicationType,
        LocalDate applicationStartDate,
        LocalDate applicationEndDate,
        PolicyAvailabilityResponse availability,
        List<CategoryResponse> categories,
        RegionScope regionScope,
        List<RegionResponse> regions,
        Integer minimumAge,
        Integer maximumAge
) {

    public ChatPolicyCardResponse {
        categories = categories != null ? List.copyOf(categories) : List.of();
        regions = regions != null ? List.copyOf(regions) : List.of();
    }

    public static ChatPolicyCardResponse of(PolicySummaryResponse summary, String reason, String highlight,
                                            EligibilityStatus eligibilityStatus) {
        return new ChatPolicyCardResponse(summary.id(), summary.title(), summary.summary(), reason, highlight,
                eligibilityStatus, summary.applicationType(), summary.applicationStartDate(),
                summary.applicationEndDate(), summary.availability(), summary.categories(), summary.regionScope(),
                summary.regions(), summary.minimumAge(), summary.maximumAge());
    }
}
