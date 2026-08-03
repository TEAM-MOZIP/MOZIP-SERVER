package com.mozip.server.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mozip.server.bookmark.entity.Bookmark;
import com.mozip.server.bookmark.repository.BookmarkRepository;
import com.mozip.server.notification.entity.Notification;
import com.mozip.server.notification.repository.NotificationRepository;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.user.entity.OAuthProvider;
import com.mozip.server.user.entity.User;
import com.mozip.server.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class NotificationBatchServiceTest {

    private static final String KEYWORD = "알림배치테스트정책";

    @Autowired
    private NotificationBatchService notificationBatchService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private NotificationWriter notificationWriter;

    @Test
    void 정확히_D_3인_북마크_정책만_알림_대상이다() {
        User user = createUser("batch-d3@example.com", "batch-d3-1");
        Policy d2 = createPolicy(KEYWORD + "-D2", today().plusDays(2), PolicyStatus.OPEN);
        Policy d3 = createPolicy(KEYWORD + "-D3", today().plusDays(3), PolicyStatus.OPEN);
        Policy d4 = createPolicy(KEYWORD + "-D4", today().plusDays(4), PolicyStatus.OPEN);
        bookmark(user, d2);
        bookmark(user, d3);
        bookmark(user, d4);

        notificationBatchService.generateClosingSoonNotifications();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationWriter, times(1)).create(captor.capture());
        assertThat(captor.getValue().getPolicy().getId()).isEqualTo(d3.getId());
        assertThat(captor.getValue().getUser().getId()).isEqualTo(user.getId());
        assertThat(captor.getValue().getTitle()).isEqualTo("북마크한 정책이 곧 마감돼요");
        assertThat(captor.getValue().getContent()).contains(d3.getTitle()).contains("3일");
    }

    @Test
    void 상시모집_정책은_알림_대상에서_제외된다() {
        User user = createUser("batch-always@example.com", "batch-always-1");
        Policy always = createPolicy(KEYWORD + "-상시", null, PolicyStatus.ALWAYS_OPEN);
        bookmark(user, always);

        notificationBatchService.generateClosingSoonNotifications();

        verify(notificationWriter, never()).create(any());
    }

    @Test
    void 신청_불가_상태의_정책은_알림_대상에서_제외된다() {
        User user = createUser("batch-suspended@example.com", "batch-suspended-1");
        Policy suspended = createPolicy(KEYWORD + "-중단", today().plusDays(3), PolicyStatus.SUSPENDED);
        bookmark(user, suspended);

        notificationBatchService.generateClosingSoonNotifications();

        verify(notificationWriter, never()).create(any());
    }

    @Test
    void 북마크하지_않은_사용자는_알림_대상이_아니다() {
        createPolicy(KEYWORD + "-북마크없음", today().plusDays(3), PolicyStatus.OPEN);

        notificationBatchService.generateClosingSoonNotifications();

        verify(notificationWriter, never()).create(any());
    }

    @Test
    void 이미_알림이_있는_사용자_정책_쌍은_다시_생성하지_않는다() {
        User user = createUser("batch-existing@example.com", "batch-existing-1");
        Policy policy = createPolicy(KEYWORD + "-이미알림", today().plusDays(3), PolicyStatus.OPEN);
        bookmark(user, policy);
        entityManager.persist(Notification.builder().user(user).policy(policy)
                .title("북마크한 정책이 곧 마감돼요").content("기존 알림").build());
        entityManager.flush();

        notificationBatchService.generateClosingSoonNotifications();

        verify(notificationWriter, never()).create(any());
    }

    @Test
    void 다른_사용자의_기존_알림_때문에_현재_사용자가_제외되지_않는다() {
        User userA = createUser("batch-cross-a@example.com", "batch-cross-a-1");
        User userB = createUser("batch-cross-b@example.com", "batch-cross-b-1");
        Policy policy = createPolicy(KEYWORD + "-교차오염", today().plusDays(3), PolicyStatus.OPEN);
        bookmark(userA, policy);
        bookmark(userB, policy);
        entityManager.persist(Notification.builder().user(userA).policy(policy)
                .title("북마크한 정책이 곧 마감돼요").content("기존 알림").build());
        entityManager.flush();

        notificationBatchService.generateClosingSoonNotifications();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationWriter, times(1)).create(captor.capture());
        assertThat(captor.getValue().getUser().getId()).isEqualTo(userB.getId());
        assertThat(captor.getValue().getPolicy().getId()).isEqualTo(policy.getId());
    }

    @Test
    void 여러_사용자에게_각각_알림_대상이_판단된다() {
        User userA = createUser("batch-multi-a@example.com", "batch-multi-a-1");
        User userB = createUser("batch-multi-b@example.com", "batch-multi-b-1");
        Policy policy = createPolicy(KEYWORD + "-여러사용자", today().plusDays(3), PolicyStatus.OPEN);
        bookmark(userA, policy);
        bookmark(userB, policy);

        notificationBatchService.generateClosingSoonNotifications();

        verify(notificationWriter, times(2)).create(any());
    }

    @Test
    void 생성_충돌이_발생해도_나머지_후보는_계속_처리된다() {
        User userA = createUser("batch-conflict-a@example.com", "batch-conflict-a-1");
        User userB = createUser("batch-conflict-b@example.com", "batch-conflict-b-1");
        Policy policyA = createPolicy(KEYWORD + "-충돌A", today().plusDays(3), PolicyStatus.OPEN);
        Policy policyB = createPolicy(KEYWORD + "-충돌B", today().plusDays(3), PolicyStatus.OPEN);
        bookmark(userA, policyA);
        bookmark(userB, policyB);
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(notificationWriter)
                .create(argThat(notification -> notification.getPolicy().getId().equals(policyA.getId())));

        notificationBatchService.generateClosingSoonNotifications();

        verify(notificationWriter).create(argThat(notification -> notification.getPolicy().getId().equals(policyB.getId())));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private User createUser(String email, String providerUserId) {
        return userRepository.save(User.builder()
                .email(email)
                .provider(OAuthProvider.KAKAO)
                .providerUserId(providerUserId)
                .build());
    }

    private Policy createPolicy(String title, LocalDate applicationEndDate, PolicyStatus status) {
        Organization organization = Organization.builder()
                .name("테스트기관")
                .type("중앙부처")
                .build();
        entityManager.persist(organization);

        Policy policy = Policy.builder()
                .organization(organization)
                .title(title)
                .applicationType(applicationEndDate != null ? ApplicationType.PERIOD : ApplicationType.ALWAYS)
                .applicationStartDate(applicationEndDate != null ? applicationEndDate.minusDays(30) : null)
                .applicationEndDate(applicationEndDate)
                .regionScope(RegionScope.NATIONAL)
                .status(status)
                .build();
        return policyRepository.save(policy);
    }

    private Bookmark bookmark(User user, Policy policy) {
        return bookmarkRepository.save(Bookmark.builder().user(user).policy(policy).build());
    }
}
