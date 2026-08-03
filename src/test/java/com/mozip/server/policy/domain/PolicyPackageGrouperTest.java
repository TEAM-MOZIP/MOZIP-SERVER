package com.mozip.server.policy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Category;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PolicyPackageGrouperTest {

    @Test
    void 패키지_순서는_category_name_오름차순이다() {
        Category categoryNa = category(1L, "나카테고리");
        Category categoryGa = category(2L, "가카테고리");
        PolicyAvailabilityCandidate candidate = candidate(1L);
        Map<Long, List<Category>> categoriesByPolicyId = Map.of(1L, List.of(categoryNa, categoryGa));

        Map<Category, List<PolicyAvailabilityCandidate>> grouped =
                PolicyPackageGrouper.group(List.of(candidate), PolicyAvailabilityCandidate::policy, categoriesByPolicyId);

        assertThat(grouped.keySet()).extracting(Category::getName).containsExactly("가카테고리", "나카테고리");
    }

    @Test
    void 패키지_내부_후보_순서는_입력_순서를_그대로_유지한다() {
        Category category = category(1L, "카테고리");
        PolicyAvailabilityCandidate first = candidate(1L);
        PolicyAvailabilityCandidate second = candidate(2L);
        PolicyAvailabilityCandidate third = candidate(3L);
        Map<Long, List<Category>> categoriesByPolicyId = Map.of(
                1L, List.of(category), 2L, List.of(category), 3L, List.of(category));

        Map<Category, List<PolicyAvailabilityCandidate>> grouped = PolicyPackageGrouper.group(
                List.of(first, second, third), PolicyAvailabilityCandidate::policy, categoriesByPolicyId);

        assertThat(grouped.get(category)).containsExactly(first, second, third);
    }

    @Test
    void 그룹당_최대_5개까지만_포함된다() {
        Category category = category(1L, "카테고리");
        List<PolicyAvailabilityCandidate> candidates = new ArrayList<>();
        Map<Long, List<Category>> categoriesByPolicyId = new HashMap<>();
        for (long id = 1; id <= 6; id++) {
            candidates.add(candidate(id));
            categoriesByPolicyId.put(id, List.of(category));
        }

        Map<Category, List<PolicyAvailabilityCandidate>> grouped =
                PolicyPackageGrouper.group(candidates, PolicyAvailabilityCandidate::policy, categoriesByPolicyId);

        assertThat(grouped.get(category)).hasSize(5);
        assertThat(grouped.get(category)).extracting(c -> c.policy().getId())
                .containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void 한_정책이_여러_카테고리에_속하면_모든_패키지에_포함된다() {
        Category categoryA = category(1L, "A카테고리");
        Category categoryB = category(2L, "B카테고리");
        PolicyAvailabilityCandidate candidate = candidate(1L);
        Map<Long, List<Category>> categoriesByPolicyId = Map.of(1L, List.of(categoryA, categoryB));

        Map<Category, List<PolicyAvailabilityCandidate>> grouped =
                PolicyPackageGrouper.group(List.of(candidate), PolicyAvailabilityCandidate::policy, categoriesByPolicyId);

        assertThat(grouped).hasSize(2);
        assertThat(grouped.get(categoryA)).containsExactly(candidate);
        assertThat(grouped.get(categoryB)).containsExactly(candidate);
    }

    @Test
    void 카테고리가_없는_정책은_결과에서_제외된다() {
        PolicyAvailabilityCandidate candidate = candidate(1L);

        Map<Category, List<PolicyAvailabilityCandidate>> grouped =
                PolicyPackageGrouper.group(List.of(candidate), PolicyAvailabilityCandidate::policy, Map.of());

        assertThat(grouped).isEmpty();
    }

    @Test
    void 빈_입력이면_빈_결과를_반환한다() {
        Map<Category, List<PolicyAvailabilityCandidate>> grouped =
                PolicyPackageGrouper.group(List.of(), PolicyAvailabilityCandidate::policy, Map.of());

        assertThat(grouped).isEmpty();
    }

    private PolicyAvailabilityCandidate candidate(Long id) {
        Policy policy = Policy.builder()
                .title("테스트 정책 " + id)
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.ALWAYS_OPEN)
                .build();
        ReflectionTestUtils.setField(policy, "id", id);

        PolicyAvailabilityResult availabilityResult =
                new PolicyAvailabilityResult(PolicyAvailability.AVAILABLE, PolicyAvailabilityReason.ALWAYS_OPEN, false);
        return new PolicyAvailabilityCandidate(policy, availabilityResult);
    }

    private Category category(Long id, String name) {
        Category category = Category.builder().code("CODE_" + id).name(name).build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
