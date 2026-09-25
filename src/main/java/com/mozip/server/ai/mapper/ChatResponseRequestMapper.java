package com.mozip.server.ai.mapper;

import com.mozip.server.ai.dto.ChatTurn;
import com.mozip.server.ai.dto.GroundingPolicy;
import com.mozip.server.ai.dto.PolicyDetailGrounding;
import com.mozip.server.chat.dto.ChatPolicyMatchResult;
import com.mozip.server.policy.dto.PolicyDetailResponse;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.PolicyApplicationInfo;
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
    /** AI에 넘기는 대화 이력은 최근 턴만 쓴다. 이력이 길수록 프롬프트가 커져 응답이 느려진다. */
    static final int MAX_HISTORY_TURNS = 4;
    /** 이전 챗봇 답변은 맥락 파악용이라 앞부분만 넘긴다. */
    static final int MAX_REPLY_LENGTH = 500;
    private static final String TRUNCATED_SUFFIX = "…(생략)";
    /** 정책 목록 근거의 지원 대상·지원 내용은 추천 이유·비교표를 쓸 만큼만 앞부분을 넘긴다(정책 5개 기준 프롬프트 크기 제한). */
    static final int MAX_GROUNDING_TEXT_LENGTH = 200;

    private ChatResponseRequestMapper() {
    }

    public static List<ChatTurn> toAiChatTurns(List<com.mozip.server.chat.dto.ChatTurn> history) {
        int from = Math.max(0, history.size() - MAX_HISTORY_TURNS);
        return history.subList(from, history.size()).stream()
                .map(turn -> new ChatTurn(turn.message(), truncateReply(turn.reply())))
                .toList();
    }

    private static String truncateReply(String reply) {
        if (reply == null || reply.length() <= MAX_REPLY_LENGTH) {
            return reply;
        }
        return reply.substring(0, MAX_REPLY_LENGTH) + TRUNCATED_SUFFIX;
    }

    public static List<GroundingPolicy> toGroundingPolicies(List<ChatPolicyMatchResult> matches) {
        return matches.stream()
                .map(match -> new GroundingPolicy(
                        match.policy().getId(),
                        match.policy().getTitle(),
                        match.eligibilityResult().overallStatus(),
                        match.policy().getApplicationEndDate(),
                        blankToNull(match.policy().getSummary()),
                        truncate(match.policy().getTargetDescription(), MAX_GROUNDING_TEXT_LENGTH),
                        truncate(match.policy().getBenefitDescription(), MAX_GROUNDING_TEXT_LENGTH)))
                .toList();
    }

    public static PolicyDetailGrounding toPolicyDetailGrounding(PolicyDetailResponse detail) {
        return toPolicyDetailGrounding(detail, null);
    }

    /**
     * 신청 정보(policy_application_info)가 있으면 신청 방법·준비서류·문의처·신청 주소까지 담는다.
     * 신청 방법은 신청 정보의 절차를 우선하고, 없으면 정책의 신청 방법을 쓴다.
     */
    public static PolicyDetailGrounding toPolicyDetailGrounding(PolicyDetailResponse detail,
                                                                PolicyApplicationInfo applicationInfo) {
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
                detail.organizationName(),
                blankToNull(detail.benefitDescription()),
                applicationInfo != null && blankToNull(applicationInfo.getApplicationProcedure()) != null
                        ? applicationInfo.getApplicationProcedure()
                        : blankToNull(detail.applicationMethod()),
                applicationInfo != null ? blankToNull(applicationInfo.getRequiredDocumentsText()) : null,
                applicationInfo != null ? blankToNull(applicationInfo.getContactInfo()) : null,
                applicationInfo != null ? blankToNull(applicationInfo.getApplicationUrl()) : null
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

    private static String truncate(String value, int maxLength) {
        String text = blankToNull(value);
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + TRUNCATED_SUFFIX;
    }

    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value : null;
    }
}
