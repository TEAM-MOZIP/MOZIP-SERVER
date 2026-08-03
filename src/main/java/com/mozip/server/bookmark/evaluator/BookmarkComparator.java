package com.mozip.server.bookmark.evaluator;

import com.mozip.server.bookmark.entity.Bookmark;
import java.util.Comparator;
import org.springframework.data.domain.Sort;

public class BookmarkComparator {

    private BookmarkComparator() {
    }

    public static Comparator<Bookmark> from(Sort sort) {
        if (sort.isUnsorted()) {
            return Comparator.comparing(Bookmark::getCreatedAt).reversed();
        }
        Comparator<Bookmark> comparator = null;
        for (Sort.Order order : sort) {
            Comparator<Bookmark> next = toComparator(order);
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        return comparator;
    }

    private static Comparator<Bookmark> toComparator(Sort.Order order) {
        return switch (order.getProperty()) {
            case "applicationEndDate" -> Comparator.comparing(
                    (Bookmark bookmark) -> bookmark.getPolicy().getApplicationEndDate(),
                    order.isAscending()
                            ? Comparator.nullsLast(Comparator.naturalOrder())
                            : Comparator.nullsLast(Comparator.reverseOrder()));
            case "createdAt" -> order.isAscending()
                    ? Comparator.comparing(Bookmark::getCreatedAt)
                    : Comparator.comparing(Bookmark::getCreatedAt).reversed();
            case "id" -> order.isAscending()
                    ? Comparator.comparing(Bookmark::getId)
                    : Comparator.comparing(Bookmark::getId).reversed();
            default -> throw new IllegalStateException("정렬 필드가 화이트리스트를 우회했습니다. field=" + order.getProperty());
        };
    }
}
