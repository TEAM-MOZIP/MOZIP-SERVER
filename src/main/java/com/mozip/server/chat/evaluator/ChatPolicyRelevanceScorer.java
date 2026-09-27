package com.mozip.server.chat.evaluator;

import com.mozip.server.policy.entity.Policy;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 키워드와 정책 텍스트의 관련도 점수를 계산한다. 제목(3점) > 요약(2점) > 지원내용·지원대상(1점) 순으로
 * 가중치를 두고, 키워드별 점수를 합산한다. "청년월세"와 "청년 월세"가 같이 매칭되도록 공백을 제거하고 비교한다.
 * 점수 0은 "관련 없음"을 뜻한다.
 */
public final class ChatPolicyRelevanceScorer {

    private static final int TITLE_WEIGHT = 3;
    private static final int SUMMARY_WEIGHT = 2;
    private static final int DETAIL_WEIGHT = 1;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ChatPolicyRelevanceScorer() {
    }

    public static int score(Policy policy, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return 0;
        }
        String title = normalize(policy.getTitle());
        String summary = normalize(policy.getSummary());
        String detail = normalize(policy.getBenefitDescription()) + normalize(policy.getTargetDescription());

        int score = 0;
        for (String keyword : keywords) {
            String normalizedKeyword = normalize(keyword);
            if (normalizedKeyword.isEmpty()) {
                continue;
            }
            if (title.contains(normalizedKeyword)) {
                score += TITLE_WEIGHT;
            }
            if (summary.contains(normalizedKeyword)) {
                score += SUMMARY_WEIGHT;
            }
            if (detail.contains(normalizedKeyword)) {
                score += DETAIL_WEIGHT;
            }
        }
        return score;
    }

    /** 키워드 중 하나라도 정책 제목에 포함되는지. 조건 없이 키워드만으로 정책 목록을 보여줄지 판단할 때 쓴다. */
    public static boolean matchesTitle(Policy policy, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        String title = normalize(policy.getTitle());
        return keywords.stream()
                .map(ChatPolicyRelevanceScorer::normalize)
                .anyMatch(keyword -> !keyword.isEmpty() && title.contains(keyword));
    }

    /** 키워드 중 하나라도 제목이나 요약에 포함되는지. 본문(지원대상·지원내용)에만 스치듯 나온 정책과 구분할 때 쓴다. */
    public static boolean matchesTitleOrSummary(Policy policy, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        String titleAndSummary = normalize(policy.getTitle()) + " " + normalize(policy.getSummary());
        return keywords.stream()
                .map(ChatPolicyRelevanceScorer::normalize)
                .anyMatch(keyword -> !keyword.isEmpty() && titleAndSummary.contains(keyword));
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
