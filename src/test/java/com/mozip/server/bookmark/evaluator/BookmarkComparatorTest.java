package com.mozip.server.bookmark.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.bookmark.entity.Bookmark;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

class BookmarkComparatorTest {

    @Test
    void applicationEndDate_오름차순에서_상시모집은_마지막이다() {
        Bookmark hasDeadline = bookmark(1L, LocalDate.of(2026, 8, 1), null);
        Bookmark always = bookmark(2L, null, null);

        Comparator<Bookmark> comparator = BookmarkComparator.from(Sort.by(Sort.Direction.ASC, "applicationEndDate"));
        List<Bookmark> sorted = List.of(always, hasDeadline).stream().sorted(comparator).toList();

        assertThat(sorted).containsExactly(hasDeadline, always);
    }

    @Test
    void applicationEndDate_내림차순에서도_상시모집은_마지막이다() {
        Bookmark hasDeadline = bookmark(1L, LocalDate.of(2026, 8, 1), null);
        Bookmark always = bookmark(2L, null, null);

        Comparator<Bookmark> comparator = BookmarkComparator.from(Sort.by(Sort.Direction.DESC, "applicationEndDate"));
        List<Bookmark> sorted = List.of(always, hasDeadline).stream().sorted(comparator).toList();

        assertThat(sorted).containsExactly(hasDeadline, always);
    }

    @Test
    void applicationEndDate_오름차순은_마감일이_이른_정책이_우선한다() {
        Bookmark earlier = bookmark(1L, LocalDate.of(2026, 8, 1), null);
        Bookmark later = bookmark(2L, LocalDate.of(2026, 9, 1), null);

        Comparator<Bookmark> comparator = BookmarkComparator.from(Sort.by(Sort.Direction.ASC, "applicationEndDate"));

        assertThat(comparator.compare(earlier, later)).isNegative();
    }

    @Test
    void applicationEndDate_내림차순은_마감일이_늦은_정책이_우선한다() {
        Bookmark earlier = bookmark(1L, LocalDate.of(2026, 8, 1), null);
        Bookmark later = bookmark(2L, LocalDate.of(2026, 9, 1), null);

        Comparator<Bookmark> comparator = BookmarkComparator.from(Sort.by(Sort.Direction.DESC, "applicationEndDate"));

        assertThat(comparator.compare(later, earlier)).isNegative();
    }

    @Test
    void 정렬_파라미터가_없으면_createdAt_내림차순을_기본으로_사용한다() {
        Bookmark older = bookmark(1L, null, LocalDateTime.of(2026, 1, 1, 0, 0));
        Bookmark newer = bookmark(2L, null, LocalDateTime.of(2026, 1, 2, 0, 0));

        Comparator<Bookmark> comparator = BookmarkComparator.from(Sort.unsorted());

        assertThat(comparator.compare(newer, older)).isNegative();
    }

    @Test
    void createdAt_내림차순은_최근_등록한_북마크가_우선한다() {
        Bookmark older = bookmark(1L, null, LocalDateTime.of(2026, 1, 1, 0, 0));
        Bookmark newer = bookmark(2L, null, LocalDateTime.of(2026, 1, 2, 0, 0));

        Comparator<Bookmark> comparator = BookmarkComparator.from(Sort.by(Sort.Direction.DESC, "createdAt"));

        assertThat(comparator.compare(newer, older)).isNegative();
    }

    @Test
    void id_tie_break가_정확히_적용된다() {
        Bookmark smallerId = bookmark(1L, LocalDate.of(2026, 8, 1), null);
        Bookmark largerId = bookmark(2L, LocalDate.of(2026, 8, 1), null);

        Comparator<Bookmark> comparator = BookmarkComparator.from(
                Sort.by(Sort.Direction.ASC, "applicationEndDate").and(Sort.by(Sort.Direction.DESC, "id")));

        assertThat(comparator.compare(largerId, smallerId)).isNegative();
    }

    private Bookmark bookmark(Long id, LocalDate applicationEndDate, LocalDateTime createdAt) {
        Policy policy = Policy.builder()
                .title("테스트 정책 " + id)
                .applicationType(ApplicationType.PERIOD)
                .applicationEndDate(applicationEndDate)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.OPEN)
                .build();

        Bookmark bookmark = Bookmark.builder().user(null).policy(policy).build();
        ReflectionTestUtils.setField(bookmark, "id", id);
        ReflectionTestUtils.setField(bookmark, "createdAt", createdAt);
        return bookmark;
    }
}
