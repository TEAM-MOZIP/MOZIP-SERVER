package com.mozip.server.policy.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mozip.server.policy.exception.InvalidSortFieldException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PolicySortValidatorTest {

    @Test
    void 허용된_필드로_정렬하면_뒤에_id_DESC_tie_break가_추가된다() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "applicationEndDate"));

        Pageable validated = PolicySortValidator.validate(pageable);

        assertThat(validated.getSort()).containsExactly(
                Sort.Order.asc("applicationEndDate"),
                Sort.Order.desc("id"));
    }

    @Test
    void 정렬_파라미터가_없으면_id_DESC만_추가된다() {
        Pageable pageable = PageRequest.of(0, 20);

        Pageable validated = PolicySortValidator.validate(pageable);

        assertThat(validated.getSort()).containsExactly(Sort.Order.desc("id"));
    }

    @Test
    void 허용되지_않은_필드로_정렬하면_예외가_발생한다() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "title"));

        assertThatThrownBy(() -> PolicySortValidator.validate(pageable))
                .isInstanceOf(InvalidSortFieldException.class);
    }

    @Test
    void id로_직접_정렬을_요청해도_예외가_발생한다() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));

        assertThatThrownBy(() -> PolicySortValidator.validate(pageable))
                .isInstanceOf(InvalidSortFieldException.class);
    }

    @Test
    void 페이지_번호와_크기는_그대로_유지된다() {
        Pageable pageable = PageRequest.of(2, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        Pageable validated = PolicySortValidator.validate(pageable);

        assertThat(validated.getPageNumber()).isEqualTo(2);
        assertThat(validated.getPageSize()).isEqualTo(10);
    }
}
