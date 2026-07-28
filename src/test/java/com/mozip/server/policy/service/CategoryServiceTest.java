package com.mozip.server.policy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.policy.dto.CategoryResponse;
import com.mozip.server.policy.entity.Category;
import com.mozip.server.policy.repository.CategoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class CategoryServiceTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void 카테고리_응답_필드가_올바르게_매핑된다() {
        Category category = categoryRepository.save(Category.builder()
                .code("CATEGORY_TEST_MAPPING")
                .name("카테고리응답테스트")
                .build());

        CategoryResponse response = categoryService.getCategories().stream()
                .filter(item -> item.id().equals(category.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(response.code()).isEqualTo("CATEGORY_TEST_MAPPING");
        assertThat(response.name()).isEqualTo("카테고리응답테스트");
    }

    @Test
    void 카테고리_목록은_id_오름차순으로_정렬된다() {
        categoryRepository.save(Category.builder()
                .code("CATEGORY_TEST_SORT_1")
                .name("카테고리정렬테스트1")
                .build());
        categoryRepository.save(Category.builder()
                .code("CATEGORY_TEST_SORT_2")
                .name("카테고리정렬테스트2")
                .build());

        List<Long> ids = categoryService.getCategories().stream()
                .map(CategoryResponse::id)
                .toList();

        assertThat(ids).isSorted();
    }
}
