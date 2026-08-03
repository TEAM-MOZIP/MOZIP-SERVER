package com.mozip.server.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mozip.server.notification.dto.NotificationResponse;
import com.mozip.server.notification.entity.Notification;
import com.mozip.server.notification.exception.NotificationNotFoundException;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 본인_알림을_최신순으로_조회한다() {
        User user = createUser("notif-list@example.com", "notif-list-1");
        Policy policyA = createPolicy("알림테스트-정책A");
        Policy policyB = createPolicy("알림테스트-정책B");
        Notification older = createNotification(user, policyA);
        Notification newer = createNotification(user, policyB);

        List<NotificationResponse> response = notificationService.getMyNotifications(user.getId());

        assertThat(response).extracting(NotificationResponse::notificationId)
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    void 알림이_없으면_빈_배열을_반환한다() {
        User user = createUser("notif-empty@example.com", "notif-empty-1");

        List<NotificationResponse> response = notificationService.getMyNotifications(user.getId());

        assertThat(response).isEmpty();
    }

    @Test
    void 생성된_알림은_읽지_않은_상태다() {
        User user = createUser("notif-unread@example.com", "notif-unread-1");
        Policy policy = createPolicy("알림테스트-안읽음");
        createNotification(user, policy);

        List<NotificationResponse> response = notificationService.getMyNotifications(user.getId());

        assertThat(response.get(0).read()).isFalse();
    }

    @Test
    void 본인_알림을_읽음_처리한다() {
        User user = createUser("notif-read@example.com", "notif-read-1");
        Policy policy = createPolicy("알림테스트-읽음");
        Notification notification = createNotification(user, policy);

        notificationService.markAsRead(user.getId(), notification.getId());

        List<NotificationResponse> response = notificationService.getMyNotifications(user.getId());
        assertThat(response.get(0).read()).isTrue();
    }

    @Test
    void 이미_읽은_알림을_다시_읽음_처리해도_예외가_발생하지_않는다() {
        User user = createUser("notif-read-twice@example.com", "notif-read-twice-1");
        Policy policy = createPolicy("알림테스트-재처리");
        Notification notification = createNotification(user, policy);
        notificationService.markAsRead(user.getId(), notification.getId());

        assertThatCode(() -> notificationService.markAsRead(user.getId(), notification.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void 타인의_알림을_읽음_처리하면_예외가_발생한다() {
        User owner = createUser("notif-owner@example.com", "notif-owner-1");
        User other = createUser("notif-other@example.com", "notif-other-1");
        Policy policy = createPolicy("알림테스트-소유권");
        Notification notification = createNotification(owner, policy);

        assertThatThrownBy(() -> notificationService.markAsRead(other.getId(), notification.getId()))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void 존재하지_않는_알림을_읽음_처리하면_예외가_발생한다() {
        User user = createUser("notif-not-found@example.com", "notif-not-found-1");

        assertThatThrownBy(() -> notificationService.markAsRead(user.getId(), 999999L))
                .isInstanceOf(NotificationNotFoundException.class);
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

    private Notification createNotification(User user, Policy policy) {
        return notificationRepository.save(Notification.builder()
                .user(user)
                .policy(policy)
                .title("북마크한 정책이 곧 마감돼요")
                .content(policy.getTitle() + " 신청 마감까지 3일 남았어요.")
                .build());
    }
}
