package com.mozip.server.policy.domain;

import com.mozip.server.policy.entity.Category;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 대상자별 정책 패키지({@link PolicyPackage})에 들어갈 정책을 고르고 섹션에 배정한다.
 *
 * <ul>
 *   <li>신청 상태: 마감(신청기간 종료·CLOSED 등)은 제외하고, 접수 중·신청기간 확인 필요·예정만 포함한다.</li>
 *   <li>대상: 나이 제한이 패키지 나이와 비슷하면(±{@value #AGE_RANGE_SLACK}세 안) 포함한다. 나이 제한이 없거나
 *       "19세 이상"처럼 넓으면 대상 표현(청년·어르신 등)이 있을 때만 포함하고, 패키지 나이와 아예 안 겹치면 제외한다.</li>
 *   <li>섹션: 배정 순서상 처음 맞는 섹션 하나에만 넣는다. 단 공유 섹션({@code exclusive=false})에는 함께 넣는다.</li>
 * </ul>
 * 카테고리·키워드 어느 섹션에도 맞지 않는 정책은 패키지에서 빠진다.
 */
public final class PolicyPackageSelector {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /**
     * 정부24는 "19세 이상", "18~65세"처럼 넓은 나이 제한이 대부분이라 "나이가 겹치면 포함"으로 두면 무관한 일반 정책이
     * 대거 들어온다. 정책 나이 범위가 패키지 범위에서 이 값 이내로 벗어날 때만 "패키지 대상 나이를 명시한 정책"으로 본다.
     */
    static final int AGE_RANGE_SLACK = 5;

    private PolicyPackageSelector() {
    }

    /**
     * 후보를 패키지 섹션별로 묶는다. 결과는 패키지의 섹션 표시 순서를 따르며 빈 섹션도 포함한다.
     * 섹션 안의 순서는 {@code candidates}의 순서를 그대로 유지하므로, 호출하는 쪽에서 미리 정렬해 넘긴다.
     */
    public static <T> Map<PolicyPackageSection, List<T>> select(
            PolicyPackage policyPackage,
            List<T> candidates,
            Function<T, Policy> policyOf,
            Function<T, PolicyAvailabilityResult> availabilityOf,
            Map<Long, PolicyEligibility> eligibilityByPolicyId,
            Map<Long, List<Category>> categoriesByPolicyId) {
        Map<PolicyPackageSection, List<T>> grouped = new LinkedHashMap<>();
        policyPackage.getSections().forEach(section -> grouped.put(section, new ArrayList<>()));

        for (T candidate : candidates) {
            Policy policy = policyOf.apply(candidate);
            if (!isOpenOrUpcoming(availabilityOf.apply(candidate))
                    || !isTargeted(policyPackage, policy, eligibilityByPolicyId.get(policy.getId()))) {
                continue;
            }
            Set<String> categoryCodes = categoriesByPolicyId.getOrDefault(policy.getId(), List.of()).stream()
                    .map(Category::getCode)
                    .collect(Collectors.toSet());
            sectionsOf(policyPackage, policy, categoryCodes)
                    .forEach(section -> grouped.get(section).add(candidate));
        }
        return grouped;
    }

    /** 패키지 전체의 정책 수(여러 섹션에 들어간 정책은 한 번만 센다). */
    public static <T> int countPolicies(Map<PolicyPackageSection, List<T>> grouped, Function<T, Policy> policyOf) {
        return distinctCandidates(grouped, policyOf).size();
    }

    /** 섹션에 배정된 후보를 섹션 순서대로 중복 없이 모은다. */
    public static <T> List<T> distinctCandidates(Map<PolicyPackageSection, List<T>> grouped,
                                                 Function<T, Policy> policyOf) {
        Map<Long, T> byPolicyId = new LinkedHashMap<>();
        grouped.values().forEach(bucket -> bucket.forEach(
                candidate -> byPolicyId.putIfAbsent(policyOf.apply(candidate).getId(), candidate)));
        return List.copyOf(byPolicyId.values());
    }

    /** 접수 중(상시 포함)·신청기간 확인 필요·예정이면 true. 신청기간이 끝났거나 CLOSED·DRAFT·SUSPENDED면 false. */
    public static boolean isOpenOrUpcoming(PolicyAvailabilityResult availabilityResult) {
        return availabilityResult.status() != PolicyAvailability.UNAVAILABLE
                || availabilityResult.reason() == PolicyAvailabilityReason.BEFORE_APPLICATION_PERIOD;
    }

    public static boolean isTargeted(PolicyPackage policyPackage, Policy policy, PolicyEligibility eligibility) {
        if (hasAgeLimit(eligibility) && !ageOverlaps(policyPackage, eligibility)) {
            return false;
        }
        return fitsAgeRange(policyPackage, eligibility) || mentionsTarget(policyPackage, policy);
    }

    /** 나이 제한이 패키지 나이와 겹치고, 범위도 패키지와 비슷한지(±{@value #AGE_RANGE_SLACK}세). */
    public static boolean fitsAgeRange(PolicyPackage policyPackage, PolicyEligibility eligibility) {
        if (!hasAgeLimit(eligibility) || !ageOverlaps(policyPackage, eligibility)) {
            return false;
        }
        int policyMin = Objects.requireNonNullElse(eligibility.getMinimumAge(), 0);
        boolean lowerFits = policyPackage.getMinimumAge() == null
                || policyMin >= policyPackage.getMinimumAge() - AGE_RANGE_SLACK;
        boolean upperFits = policyPackage.getMaximumAge() == null
                || (eligibility.getMaximumAge() != null
                && eligibility.getMaximumAge() <= policyPackage.getMaximumAge() + AGE_RANGE_SLACK);
        return lowerFits && upperFits;
    }

    public static boolean hasAgeLimit(PolicyEligibility eligibility) {
        return eligibility != null && (eligibility.getMinimumAge() != null || eligibility.getMaximumAge() != null);
    }

    static boolean ageOverlaps(PolicyPackage policyPackage, PolicyEligibility eligibility) {
        int policyMin = Objects.requireNonNullElse(eligibility.getMinimumAge(), 0);
        int policyMax = Objects.requireNonNullElse(eligibility.getMaximumAge(), Integer.MAX_VALUE);
        int packageMin = Objects.requireNonNullElse(policyPackage.getMinimumAge(), 0);
        int packageMax = Objects.requireNonNullElse(policyPackage.getMaximumAge(), Integer.MAX_VALUE);
        return policyMin <= packageMax && policyMax >= packageMin;
    }

    static boolean mentionsTarget(PolicyPackage policyPackage, Policy policy) {
        String text = normalize(policy.getTitle()) + " " + normalize(policy.getSummary()) + " "
                + normalize(policy.getTargetDescription());
        for (String excluded : policyPackage.getExcludedTargetTerms()) {
            text = text.replace(normalize(excluded), " ");
        }
        String normalizedText = text;
        return policyPackage.getTargetTerms().stream()
                .map(PolicyPackageSelector::normalize)
                .anyMatch(normalizedText::contains);
    }

    static List<PolicyPackageSection> sectionsOf(PolicyPackage policyPackage, Policy policy, Set<String> categoryCodes) {
        String text = normalize(policy.getTitle()) + " " + normalize(policy.getSummary());
        List<PolicyPackageSection> matched = new ArrayList<>();
        boolean exclusiveAssigned = false;
        for (PolicyPackageSection section : policyPackage.assignmentOrder()) {
            if (!section.matches(categoryCodes, text)) {
                continue;
            }
            if (!section.exclusive()) {
                matched.add(section);
            } else if (!exclusiveAssigned) {
                matched.add(section);
                exclusiveAssigned = true;
            }
        }
        return matched;
    }

    /**
     * 공개(비로그인) 패키지 정렬: 접수 중 → 신청기간 확인 필요 → 예정, 같은 상태면 패키지 대상 나이를 명시한 정책 먼저,
     * 그다음 마감 임박(신청 마감일 오름차순) → 최신 등록 → id.
     */
    public static <T> Comparator<T> publicOrder(PolicyPackage policyPackage,
                                                Function<T, Policy> policyOf,
                                                Function<T, PolicyAvailabilityResult> availabilityOf,
                                                Map<Long, PolicyEligibility> eligibilityByPolicyId) {
        return Comparator
                .comparingInt((T candidate) -> availabilityPriority(availabilityOf.apply(candidate).status()))
                .thenComparingInt(candidate -> fitsAgeRange(policyPackage,
                        eligibilityByPolicyId.get(policyOf.apply(candidate).getId())) ? 0 : 1)
                .thenComparing(candidate -> policyOf.apply(candidate).getApplicationEndDate(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(candidate -> policyOf.apply(candidate).getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(candidate -> policyOf.apply(candidate).getId(),
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static int availabilityPriority(PolicyAvailability availability) {
        return switch (availability) {
            case AVAILABLE -> 0;
            case NEEDS_REVIEW -> 1;
            case UNAVAILABLE -> 2;
        };
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
