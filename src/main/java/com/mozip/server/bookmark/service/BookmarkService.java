package com.mozip.server.bookmark.service;

import com.mozip.server.bookmark.dto.BookmarkResponse;
import com.mozip.server.bookmark.entity.Bookmark;
import com.mozip.server.bookmark.evaluator.BookmarkComparator;
import com.mozip.server.bookmark.exception.BookmarkAlreadyExistsException;
import com.mozip.server.bookmark.repository.BookmarkRepository;
import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.evaluator.PolicyAvailabilityEvaluator;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.user.entity.User;
import com.mozip.server.user.exception.UserNotFoundException;
import com.mozip.server.user.repository.UserRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;
    private final PolicyRepository policyRepository;
    private final PolicyAvailabilityEvaluator policyAvailabilityEvaluator;

    public BookmarkService(BookmarkRepository bookmarkRepository, UserRepository userRepository,
                            PolicyRepository policyRepository, PolicyAvailabilityEvaluator policyAvailabilityEvaluator) {
        this.bookmarkRepository = bookmarkRepository;
        this.userRepository = userRepository;
        this.policyRepository = policyRepository;
        this.policyAvailabilityEvaluator = policyAvailabilityEvaluator;
    }

    @Transactional
    public BookmarkResponse addBookmark(Long userId, Long policyId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Policy policy = policyRepository.findWithOrganizationById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException(policyId));

        try {
            Bookmark saved = bookmarkRepository.save(Bookmark.builder().user(user).policy(policy).build());
            return BookmarkResponse.from(saved, policyAvailabilityEvaluator.evaluate(policy));
        } catch (DataIntegrityViolationException e) {
            throw new BookmarkAlreadyExistsException(userId, policyId);
        }
    }

    public PageResponse<BookmarkResponse> getMyBookmarks(Long userId, Pageable pageable) {
        List<Bookmark> bookmarks = bookmarkRepository.findByUserId(userId).stream()
                .sorted(BookmarkComparator.from(pageable.getSort()))
                .toList();
        return toPageResponse(bookmarks, pageable);
    }

    private PageResponse<BookmarkResponse> toPageResponse(List<Bookmark> bookmarks, Pageable pageable) {
        int totalElements = bookmarks.size();
        int size = pageable.getPageSize();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        long offset = pageable.getOffset();

        List<Bookmark> pageContent = offset >= totalElements
                ? List.of()
                : bookmarks.subList((int) offset, (int) Math.min(offset + size, totalElements));

        List<BookmarkResponse> content = pageContent.stream()
                .map(bookmark -> BookmarkResponse.from(bookmark, policyAvailabilityEvaluator.evaluate(bookmark.getPolicy())))
                .toList();

        boolean first = pageable.getPageNumber() == 0;
        boolean last = pageable.getPageNumber() >= totalPages - 1;

        return new PageResponse<>(content, pageable.getPageNumber(), size, totalElements, totalPages, first, last);
    }

    @Transactional
    public void removeBookmark(Long userId, Long policyId) {
        bookmarkRepository.deleteByUserIdAndPolicyId(userId, policyId);
    }
}
