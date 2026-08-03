package com.mozip.server.policy.domain;

import com.mozip.server.policy.entity.Category;
import com.mozip.server.policy.entity.Policy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PolicyPackageGrouper {

    private static final int MAX_POLICIES_PER_PACKAGE = 5;

    private PolicyPackageGrouper() {
    }

    public static <T> Map<Category, List<T>> group(List<T> sortedCandidates, Function<T, Policy> policyExtractor,
                                                     Map<Long, List<Category>> categoriesByPolicyId) {
        Map<Category, List<T>> grouped = new LinkedHashMap<>();
        for (T candidate : sortedCandidates) {
            Long policyId = policyExtractor.apply(candidate).getId();
            for (Category category : categoriesByPolicyId.getOrDefault(policyId, List.of())) {
                List<T> bucket = grouped.computeIfAbsent(category, key -> new ArrayList<>());
                if (bucket.size() < MAX_POLICIES_PER_PACKAGE) {
                    bucket.add(candidate);
                }
            }
        }
        return grouped.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getName()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }
}
