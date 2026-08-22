package com.mozip.server.ai.mapper;

import com.mozip.server.ai.dto.GroundingPolicy;
import com.mozip.server.ai.dto.PolicyDetailGrounding;
import com.mozip.server.chat.dto.ChatPolicyMatchResult;
import com.mozip.server.policy.dto.PolicyDetailResponse;
import com.mozip.server.policy.entity.ApplicationType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * D-1 평가 결과와 {@link PolicyDetailResponse}를 MOZIP-AI {@code /chat/respond} wire
 * shape으로 축소·변환한다. AI가 SERVER 도메인 모델({@code regionScope}/{@code sourceUrl}/
 * {@code bookmarked} 등)을 알 필요가 없다는 Technical Design 결정에 따른다.
 */
public class ChatResponseRequestMapper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String NO_ELIGIBILITY_TEXT = "자격 조건 정보가 등록되지 않음";
    private static final String NO_SUMMARY_TEXT = "요약 정보가 등록되지 않음";

    private ChatResponseRequestMapper() {
    }

    public static List<GroundingPolicy> toGroundingPolicies(List<ChatPolicyMatchResult> matches) {
        return matches.stream()
                .map(match -> new GroundingPolicy(
                        match.policy().getId(),
                        match.policy().getTitle(),
                        match.eligibilityResult().overallStatus(),
                        match.policy().getApplicationEndDate()))
                .toList();
    }

    public static PolicyDetailGrounding toPolicyDetailGrounding(PolicyDetailResponse detail) {
        String summary = detail.summary() != null && !detail.summary().isBlank()
                ? detail.summary()
                : detail.description() != null && !detail.description().isBlank() ? detail.description() : NO_SUMMARY_TEXT;
        String eligibility = detail.targetDescription() != null && !detail.targetDescription().isBlank()
                ? detail.targetDescription()
                : NO_ELIGIBILITY_TEXT;

        return new PolicyDetailGrounding(
                detail.title(),
                summary,
                eligibility,
                applicationPeriodText(detail.applicationType(), detail.applicationStartDate(), detail.applicationEndDate()),
                detail.organizationName()
        );
    }

    private static String applicationPeriodText(ApplicationType applicationType, LocalDate startDate, LocalDate endDate) {
        return switch (applicationType) {
            case ALWAYS -> "상시 신청 가능";
            case PERIOD -> formatPeriod(startDate, endDate);
            case UNKNOWN -> "신청 기간 확인 필요";
        };
    }

    private static String formatPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return "신청 기간 확인 필요";
        }
        String start = startDate != null ? startDate.format(DATE_FORMATTER) : "확인 필요";
        String end = endDate != null ? endDate.format(DATE_FORMATTER) : "확인 필요";
        return start + " ~ " + end;
    }
}
