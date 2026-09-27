package com.mozip.server.bookmark.service;

import com.mozip.server.bookmark.dto.BookmarkResponse;
import com.mozip.server.bookmark.entity.Bookmark;
import com.mozip.server.bookmark.evaluator.BookmarkComparator;
import com.mozip.server.bookmark.exception.BookmarkAlreadyExistsException;
import com.mozip.server.bookmark.repository.BookmarkRepository;
import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.entity.Category;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyCategory;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyRegion;
import com.mozip.server.policy.evaluator.PolicyAvailabilityEvaluator;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyCategoryRepository;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRegionRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.region.entity.Region;
import com.mozip.server.user.entity.User;
import com.mozip.server.user.exception.UserNotFoundException;
import com.mozip.server.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final PolicyCategoryRepository policyCategoryRepository;
    private final PolicyRegionRepository policyRegionRepository;
    private final PolicyEligibilityRepository policyEligibilityRepository;

    public BookmarkService(BookmarkRepository bookmarkRepository, UserRepository userRepository,
                            PolicyRepository policyRepository, PolicyAvailabilityEvaluator policyAvailabilityEvaluator,
                            PolicyCategoryRepository policyCategoryRepository,
                            PolicyRegionRepository policyRegionRepository,
                            PolicyEligibilityRepository policyEligibilityRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.userRepository = userRepository;
        this.policyRepository = policyRepository;
        this.policyAvailabilityEvaluator = policyAvailabilityEvaluator;
        this.policyCategoryRepository = policyCategoryRepository;
        this.policyRegionRepository = policyRegionRepository;
        this.policyEligibilityRepository = policyEligibilityRepository;
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

        // 카드 칩(카테고리·지역·연령)용 정보는 현재 페이지 정책들만 한 번에 조회한다.
        List<Long> policyIds = pageContent.stream().map(bookmark -> bookmark.getPolicy().getId()).toList();
        Map<Long, List<Category>> categoriesByPolicyId = policyIds.isEmpty()
                ? Map.of()
                : policyCategoryRepository.findByPolicyIdIn(policyIds).stream()
                        .collect(Collectors.groupingBy(policyCategory -> policyCategory.getPolicy().getId(),
                                Collectors.mapping(PolicyCategory::getCategory, Collectors.toList())));
        Map<Long, List<Region>> regionsByPolicyId = policyIds.isEmpty()
                ? Map.of()
                : policyRegionRepository.findByPolicyIdIn(policyIds).stream()
                        .collect(Collectors.groupingBy(policyRegion -> policyRegion.getPolicy().getId(),
                                Collectors.mapping(PolicyRegion::getRegion, Collectors.toList())));
        Map<Long, PolicyEligibility> eligibilityByPolicyId = policyIds.isEmpty()
                ? Map.of()
                : policyEligibilityRepository.findByPolicyIdIn(policyIds).stream()
                        .collect(Collectors.toMap(eligibility -> eligibility.getPolicy().getId(), Function.identity()));

        List<BookmarkResponse> content = pageContent.stream()
                .map(bookmark -> {
                    Long policyId = bookmark.getPolicy().getId();
                    return BookmarkResponse.from(bookmark, policyAvailabilityEvaluator.evaluate(bookmark.getPolicy()),
                            categoriesByPolicyId.getOrDefault(policyId, List.of()),
                            regionsByPolicyId.getOrDefault(policyId, List.of()),
                            eligibilityByPolicyId.get(policyId));
                })
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
