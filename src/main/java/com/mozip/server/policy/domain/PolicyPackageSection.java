package com.mozip.server.policy.domain;

import java.util.List;
import java.util.Set;

/**
 * 대상자별 정책 패키지 안의 섹션(예: 취업준비생 패키지의 "취업" 섹션) 정의.
 *
 * <p>정책이 섹션에 해당하는지는 {@link MatchMode}에 따라 카테고리 코드와 키워드(정책 제목·요약)로 판단한다.
 * 교통·월세처럼 카테고리가 따로 없는 주제는 키워드로 묶는다.
 *
 * @param exclusive true면 정책이 "배정 순서상 처음 맞는 한 섹션"에만 들어간다.
 *                  false면 다른 섹션에 이미 배정됐어도 이 섹션에 함께 노출된다(예: 취업준비생의 창업 섹션).
 */
public record PolicyPackageSection(
        String key,
        String name,
        Set<String> categoryCodes,
        List<String> keywords,
        MatchMode matchMode,
        boolean exclusive
) {

    /** 교통 섹션 키워드. 카테고리에 교통이 없어 제목·요약 키워드로 판단한다. */
    public static final List<String> TRANSPORT_KEYWORDS = List.of("교통", "버스", "지하철", "철도", "택시", "운전");

    public enum MatchMode {
        /** 카테고리 코드 중 하나라도 일치 */
        CATEGORY,
        /** 키워드 중 하나라도 제목·요약에 포함 */
        KEYWORD,
        /** 카테고리가 일치하면서 키워드도 포함(예: 주거 중 월세·전세) */
        CATEGORY_AND_KEYWORD,
        /** 카테고리가 일치하거나 키워드가 포함(예: 취업 + 연금·노후) */
        CATEGORY_OR_KEYWORD
    }

    public PolicyPackageSection {
        categoryCodes = Set.copyOf(categoryCodes);
        keywords = List.copyOf(keywords);
    }

    public static PolicyPackageSection category(String key, String name, String... categoryCodes) {
        return new PolicyPackageSection(key, name, Set.of(categoryCodes), List.of(), MatchMode.CATEGORY, true);
    }

    public static PolicyPackageSection keyword(String key, String name, List<String> keywords) {
        return new PolicyPackageSection(key, name, Set.of(), keywords, MatchMode.KEYWORD, true);
    }

    public static PolicyPackageSection categoryAndKeyword(String key, String name, List<String> keywords,
                                                          String... categoryCodes) {
        return new PolicyPackageSection(key, name, Set.of(categoryCodes), keywords, MatchMode.CATEGORY_AND_KEYWORD,
                true);
    }

    public static PolicyPackageSection categoryOrKeyword(String key, String name, List<String> keywords,
                                                         String... categoryCodes) {
        return new PolicyPackageSection(key, name, Set.of(categoryCodes), keywords, MatchMode.CATEGORY_OR_KEYWORD,
                true);
    }

    /** 다른 섹션에 배정된 정책도 함께 노출하는 섹션으로 바꾼다. */
    public PolicyPackageSection shared() {
        return new PolicyPackageSection(key, name, categoryCodes, keywords, matchMode, false);
    }

    public boolean hasKeywords() {
        return !keywords.isEmpty();
    }

    /**
     * @param policyCategoryCodes 정책에 연결된 카테고리 코드
     * @param normalizedText      공백을 제거한 정책 제목 + 요약
     */
    public boolean matches(Set<String> policyCategoryCodes, String normalizedText) {
        boolean categoryMatched = policyCategoryCodes.stream().anyMatch(categoryCodes::contains);
        boolean keywordMatched = keywords.stream()
                .map(PolicyPackageSelector::normalize)
                .anyMatch(normalizedText::contains);
        return switch (matchMode) {
            case CATEGORY -> categoryMatched;
            case KEYWORD -> keywordMatched;
            case CATEGORY_AND_KEYWORD -> categoryMatched && keywordMatched;
            case CATEGORY_OR_KEYWORD -> categoryMatched || keywordMatched;
        };
    }
}
