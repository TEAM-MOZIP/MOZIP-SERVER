package com.mozip.server.chat.evaluator;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 사용자가 쓰는 말과 정책 제목에 쓰이는 말이 다른 경우를 메우는 작은 동의어 사전이다.
 * 예: "대학생"을 물으면 제목에 "대학생"이 없는 학자금·장학금 정책도 찾도록 "학자금", "장학"을 덧붙인다.
 * 원래 키워드를 앞에 두고 동의어를 뒤에 붙인다(중복 제거).
 */
public final class ChatKeywordExpander {

    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("대학생", List.of("대학", "학자금", "장학")),
            Map.entry("대학원생", List.of("대학원", "학자금", "장학")),
            Map.entry("학생", List.of("장학", "학자금")),
            Map.entry("취준생", List.of("구직", "취업")),
            Map.entry("구직자", List.of("구직", "취업")),
            Map.entry("백수", List.of("구직", "취업")),
            Map.entry("일자리", List.of("취업", "고용")),
            Map.entry("월세", List.of("임차", "주거비")),
            Map.entry("전세", List.of("임차", "주거")),
            Map.entry("자취", List.of("주거", "월세")),
            Map.entry("집", List.of("주거", "주택")),
            Map.entry("노인", List.of("어르신", "경로")),
            Map.entry("어르신", List.of("노인", "경로")),
            Map.entry("아기", List.of("영유아", "출산", "아동")),
            Map.entry("아이", List.of("아동", "영유아")),
            Map.entry("임신", List.of("임산부", "출산")),
            Map.entry("장애인", List.of("장애")),
            Map.entry("병원비", List.of("의료비", "진료비")),
            Map.entry("생활비", List.of("생계", "생활안정"))
    );

    private ChatKeywordExpander() {
    }

    public static List<String> expand(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        Set<String> expanded = new LinkedHashSet<>(keywords);
        for (String keyword : keywords) {
            expanded.addAll(SYNONYMS.getOrDefault(keyword, List.of()));
        }
        return List.copyOf(expanded);
    }
}
