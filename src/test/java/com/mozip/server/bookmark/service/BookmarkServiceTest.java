package com.mozip.server.bookmark.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mozip.server.bookmark.dto.BookmarkResponse;
import com.mozip.server.bookmark.exception.BookmarkAlreadyExistsException;
import com.mozip.server.bookmark.repository.BookmarkRepository;
import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.domain.PolicyAvailabilityReason;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.user.entity.OAuthProvider;
import com.mozip.server.user.entity.User;
import com.mozip.server.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class BookmarkServiceTest {

    @Autowired
    private BookmarkService bookmarkService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 북마크_등록에_성공한다() {
        User user = createUser("bookmark-add@example.com", "bookmark-add-1");
        Policy policy = createPolicy("북마크테스트-등록");

        BookmarkResponse response = bookmarkService.addBookmark(user.getId(), policy.getId());

        assertThat(response.policyId()).isEqualTo(policy.getId());
        assertThat(response.title()).isEqualTo("북마크테스트-등록");
        assertThat(response.organizationName()).isEqualTo("테스트기관");
        assertThat(response.bookmarkedAt()).isNotNull();
        assertThat(response.availability().status()).isEqualTo(PolicyAvailability.AVAILABLE);
        assertThat(response.availability().reason()).isEqualTo(PolicyAvailabilityReason.ALWAYS_OPEN);
        assertThat(response.availability().closingSoon()).isFalse();
    }

    @Test
    void 존재하지_않는_정책을_북마크하면_예외가_발생한다() {
        User user = createUser("bookmark-no-policy@example.com", "bookmark-no-policy-1");

        assertThatThrownBy(() -> bookmarkService.addBookmark(user.getId(), 999999L))
                .isInstanceOf(PolicyNotFoundException.class);
    }

    @Test
    void 이미_북마크한_정책을_다시_등록하면_예외가_발생한다() {
        User user = createUser("bookmark-dup@example.com", "bookmark-dup-1");
        Policy policy = createPolicy("북마크테스트-중복");
        bookmarkService.addBookmark(user.getId(), policy.getId());

        assertThatThrownBy(() -> bookmarkService.addBookmark(user.getId(), policy.getId()))
                .isInstanceOf(BookmarkAlreadyExistsException.class);
    }

    @Test
    void 내_북마크_목록을_조회한다() {
        User user = createUser("bookmark-list@example.com", "bookmark-list-1");
        Policy policy = createPolicy("북마크테스트-목록");
        bookmarkService.addBookmark(user.getId(), policy.getId());

        PageResponse<BookmarkResponse> response = bookmarkService.getMyBookmarks(user.getId(), PageRequest.of(0, 20));

        assertThat(response.content()).hasSize(1);
        BookmarkResponse item = response.content().get(0);
        assertThat(item.policyId()).isEqualTo(policy.getId());
        assertThat(item.title()).isEqualTo("북마크테스트-목록");
        assertThat(item.organizationName()).isEqualTo("테스트기관");
        assertThat(item.availability().status()).isEqualTo(PolicyAvailability.AVAILABLE);
        assertThat(item.availability().closingSoon()).isFalse();
    }

    @Test
    void 북마크가_없으면_빈_목록을_반환한다() {
        User user = createUser("bookmark-empty@example.com", "bookmark-empty-1");

        PageResponse<BookmarkResponse> response = bookmarkService.getMyBookmarks(user.getId(), PageRequest.of(0, 20));

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    void 최신_등록순으로_페이지네이션된다() {
        User user = createUser("bookmark-page@example.com", "bookmark-page-1");
        Policy first = createPolicy("북마크테스트-페이지1");
        bookmarkService.addBookmark(user.getId(), first.getId());
        Policy second = createPolicy("북마크테스트-페이지2");
        bookmarkService.addBookmark(user.getId(), second.getId());

        PageResponse<BookmarkResponse> response = bookmarkService.getMyBookmarks(
                user.getId(), PageRequest.of(0, 1, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id")));

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).policyId()).isEqualTo(second.getId());
    }

    @Test
    void 오프셋_계산이_오버플로되는_page_요청에서도_예외_없이_빈_목록을_반환한다() {
        User user = createUser("bookmark-offset-overflow@example.com", "bookmark-offset-overflow-1");
        Policy policy = createPolicy("북마크테스트-오프셋오버플로");
        bookmarkService.addBookmark(user.getId(), policy.getId());

        PageResponse<BookmarkResponse> response = bookmarkService.getMyBookmarks(
                user.getId(), PageRequest.of(21474837, 100));

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isTrue();
        assertThat(response.page()).isEqualTo(21474837);
        assertThat(response.size()).isEqualTo(100);
    }

    @Test
    void 신청_마감일_오름차순으로_정렬된다() {
        User user = createUser("bookmark-sort-asc@example.com", "bookmark-sort-asc-1");
        Policy later = createPolicyWithDeadline("북마크정렬-늦음", LocalDate.now().plusDays(30));
        Policy earlier = createPolicyWithDeadline("북마크정렬-이름", LocalDate.now().plusDays(10));
        bookmarkService.addBookmark(user.getId(), later.getId());
        bookmarkService.addBookmark(user.getId(), earlier.getId());

        PageResponse<BookmarkResponse> response = bookmarkService.getMyBookmarks(user.getId(), PageRequest.of(0, 20,
                Sort.by(Sort.Direction.ASC, "applicationEndDate").and(Sort.by(Sort.Direction.DESC, "id"))));

        assertThat(response.content()).extracting(BookmarkResponse::policyId)
                .containsExactly(earlier.getId(), later.getId());
    }

    @Test
    void 신청_마감일_내림차순에서도_상시모집_정책은_마지막이다() {
        User user = createUser("bookmark-sort-desc-null@example.com", "bookmark-sort-desc-null-1");
        Policy hasDeadline = createPolicyWithDeadline("북마크정렬-마감있음", LocalDate.now().plusDays(10));
        Policy always = createPolicy("북마크정렬-상시");
        bookmarkService.addBookmark(user.getId(), hasDeadline.getId());
        bookmarkService.addBookmark(user.getId(), always.getId());

        PageResponse<BookmarkResponse> response = bookmarkService.getMyBookmarks(user.getId(), PageRequest.of(0, 20,
                Sort.by(Sort.Direction.DESC, "applicationEndDate").and(Sort.by(Sort.Direction.DESC, "id"))));

        assertThat(response.content()).extracting(BookmarkResponse::policyId)
                .containsExactly(hasDeadline.getId(), always.getId());
    }

    @Test
    void 신청_마감일_정렬에도_신청불가_상태의_북마크가_포함된다() {
        User user = createUser("bookmark-sort-unavailable@example.com", "bookmark-sort-unavailable-1");
        Policy closed = createPolicyWithDeadline("북마크정렬-마감지남", LocalDate.now().minusDays(1));
        bookmarkService.addBookmark(user.getId(), closed.getId());

        PageResponse<BookmarkResponse> response = bookmarkService.getMyBookmarks(user.getId(), PageRequest.of(0, 20,
                Sort.by(Sort.Direction.ASC, "applicationEndDate").and(Sort.by(Sort.Direction.DESC, "id"))));

        assertThat(response.content()).extracting(BookmarkResponse::policyId).containsExactly(closed.getId());
        assertThat(response.content().get(0).availability().status()).isEqualTo(PolicyAvailability.UNAVAILABLE);
    }

    @Test
    void 북마크를_해제한다() {
        User user = createUser("bookmark-remove@example.com", "bookmark-remove-1");
        Policy policy = createPolicy("북마크테스트-해제");
        bookmarkService.addBookmark(user.getId(), policy.getId());

        bookmarkService.removeBookmark(user.getId(), policy.getId());

        PageResponse<BookmarkResponse> response = bookmarkService.getMyBookmarks(user.getId(), PageRequest.of(0, 20));
        assertThat(response.content()).isEmpty();
    }

    @Test
    void 존재하지_않는_북마크를_해제해도_예외가_발생하지_않는다() {
        User user = createUser("bookmark-remove-none@example.com", "bookmark-remove-none-1");

        assertThatCode(() -> bookmarkService.removeBookmark(user.getId(), 999999L))
                .doesNotThrowAnyException();
    }

    @Test
    void 다른_사용자의_북마크는_영향받지_않는다() {
        User userA = createUser("bookmark-owner-a@example.com", "bookmark-owner-a-1");
        User userB = createUser("bookmark-owner-b@example.com", "bookmark-owner-b-1");
        Policy policy = createPolicy("북마크테스트-소유권");
        bookmarkService.addBookmark(userA.getId(), policy.getId());

        bookmarkService.removeBookmark(userB.getId(), policy.getId());

        PageResponse<BookmarkResponse> response = bookmarkService.getMyBookmarks(userA.getId(), PageRequest.of(0, 20));
        assertThat(response.content()).hasSize(1);
    }

    private User createUser(String email, String providerUserId) {
        return userRepository.save(User.builder()
                .email(email)
                .provider(OAuthProvider.KAKAO)
                .providerUserId(providerUserId)
                .build());
    }

    private Policy createPolicy(String title) {
        Organization organization = Organization.builder()
                .name("테스트기관")
                .type("중앙부처")
                .build();
        entityManager.persist(organization);

        Policy policy = Policy.builder()
                .organization(organization)
                .title(title)
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.ALWAYS_OPEN)
                .build();
        return policyRepository.save(policy);
    }

    private Policy createPolicyWithDeadline(String title, LocalDate applicationEndDate) {
        Organization organization = Organization.builder()
                .name("테스트기관")
                .type("중앙부처")
                .build();
        entityManager.persist(organization);

        Policy policy = Policy.builder()
                .organization(organization)
                .title(title)
                .applicationType(ApplicationType.PERIOD)
                .applicationStartDate(applicationEndDate.minusDays(30))
                .applicationEndDate(applicationEndDate)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.OPEN)
                .build();
        return policyRepository.save(policy);
    }
}
