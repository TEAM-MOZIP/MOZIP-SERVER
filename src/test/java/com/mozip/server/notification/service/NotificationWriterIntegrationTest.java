package com.mozip.server.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * NotificationWriter.create()는 REQUIRES_NEW로 별도 트랜잭션을 열기 때문에,
 * 이 테스트는 의도적으로 클래스 레벨 @Transactional을 사용하지 않는다.
 * fixture를 미커밋 상태로 남겨두면 REQUIRES_NEW가 여는 별도 커넥션에서 FK 대상이 보이지 않아
 * 실패하므로, TransactionTemplate으로 즉시 커밋하고 각 테스트 후 수동으로 정리한다.
 */
@SpringBootTest
@ActiveProfiles("local")
class NotificationWriterIntegrationTest {

    @Autowired
    private NotificationWriter notificationWriter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private final List<Long> createdNotificationIds = new ArrayList<>();
    private final List<Long> createdPolicyIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdOrganizationIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAllByIdInBatch(createdNotificationIds);
        policyRepository.deleteAllByIdInBatch(createdPolicyIds);
        userRepository.deleteAllByIdInBatch(createdUserIds);
        transactionTemplate.executeWithoutResult(status ->
                createdOrganizationIds.forEach(id -> {
                    Organization organization = entityManager.find(Organization.class, id);
                    if (organization != null) {
                        entityManager.remove(organization);
                    }
                }));
    }

    @Test
    void REQUIRES_NEW로_저장한_알림은_실제로_커밋되고_중복_저장은_예외가_발생하며_이후_저장은_계속된다() {
        User user = createUser("writer-it@example.com", "writer-it-1");
        Policy policy = createPolicy("알림Writer통합테스트-정책");

        notificationWriter.create(Notification.builder()
                .user(user).policy(policy).title("북마크한 정책이 곧 마감돼요").content("첫 번째 저장").build());

        List<Notification> saved = notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(user.getId());
        assertThat(saved).hasSize(1);
        createdNotificationIds.add(saved.get(0).getId());

        assertThatThrownBy(() -> notificationWriter.create(Notification.builder()
                .user(user).policy(policy).title("북마크한 정책이 곧 마감돼요").content("중복 저장 시도").build()))
                .isInstanceOf(DataIntegrityViolationException.class);

        User otherUser = createUser("writer-it-other@example.com", "writer-it-other-1");
        Policy otherPolicy = createPolicy("알림Writer통합테스트-다른정책");

        notificationWriter.create(Notification.builder()
                .user(otherUser).policy(otherPolicy).title("북마크한 정책이 곧 마감돼요").content("충돌 이후 저장").build());

        List<Notification> otherSaved = notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(otherUser.getId());
        assertThat(otherSaved).hasSize(1);
        createdNotificationIds.add(otherSaved.get(0).getId());
    }

    private User createUser(String email, String providerUserId) {
        User user = userRepository.save(User.builder()
                .email(email)
                .provider(OAuthProvider.KAKAO)
                .providerUserId(providerUserId)
                .build());
        createdUserIds.add(user.getId());
        return user;
    }

    private Policy createPolicy(String title) {
        Organization organization = transactionTemplate.execute(status -> {
            Organization org = Organization.builder().name("테스트기관").type("중앙부처").build();
            entityManager.persist(org);
            return org;
        });
        createdOrganizationIds.add(organization.getId());

        Policy policy = policyRepository.save(Policy.builder()
                .organization(organization)
                .title(title)
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.ALWAYS_OPEN)
                .build());
        createdPolicyIds.add(policy.getId());
        return policy;
    }
}
