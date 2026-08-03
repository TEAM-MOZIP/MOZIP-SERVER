package com.mozip.server.notification.service;

import com.mozip.server.bookmark.entity.Bookmark;
import com.mozip.server.bookmark.repository.BookmarkRepository;
import com.mozip.server.notification.entity.Notification;
import com.mozip.server.notification.repository.NotificationRepository;
import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.evaluator.PolicyAvailabilityEvaluator;
import com.mozip.server.user.entity.User;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationBatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationBatchService.class);
    private static final long CLOSING_SOON_DAYS = 3;

    private final Clock clock;
    private final BookmarkRepository bookmarkRepository;
    private final NotificationRepository notificationRepository;
    private final PolicyAvailabilityEvaluator policyAvailabilityEvaluator;
    private final NotificationWriter notificationWriter;

    public NotificationBatchService(Clock clock, BookmarkRepository bookmarkRepository,
                                     NotificationRepository notificationRepository,
                                     PolicyAvailabilityEvaluator policyAvailabilityEvaluator,
                                     NotificationWriter notificationWriter) {
        this.clock = clock;
        this.bookmarkRepository = bookmarkRepository;
        this.notificationRepository = notificationRepository;
        this.policyAvailabilityEvaluator = policyAvailabilityEvaluator;
        this.notificationWriter = notificationWriter;
    }

    public void generateClosingSoonNotifications() {
        LocalDate targetDate = LocalDate.now(clock).plusDays(CLOSING_SOON_DAYS);
        List<Bookmark> candidates = bookmarkRepository.findByPolicyApplicationEndDate(targetDate);
        if (candidates.isEmpty()) {
            return;
        }

        List<Long> policyIds = candidates.stream().map(bookmark -> bookmark.getPolicy().getId()).distinct().toList();
        Set<BookmarkKey> existingKeys = notificationRepository.findByPolicyIdIn(policyIds).stream()
                .map(notification -> new BookmarkKey(notification.getUser().getId(), notification.getPolicy().getId()))
                .collect(Collectors.toSet());

        for (Bookmark bookmark : candidates) {
            User user = bookmark.getUser();
            Policy policy = bookmark.getPolicy();

            if (existingKeys.contains(new BookmarkKey(user.getId(), policy.getId()))) {
                continue;
            }
            if (policyAvailabilityEvaluator.evaluate(policy).status() != PolicyAvailability.AVAILABLE) {
                continue;
            }

            Notification notification = Notification.builder()
                    .user(user)
                    .policy(policy)
                    .title("북마크한 정책이 곧 마감돼요")
                    .content(policy.getTitle() + " 신청 마감까지 3일 남았어요.")
                    .build();
            try {
                notificationWriter.create(notification);
            } catch (DataIntegrityViolationException e) {
                log.info("알림 생성 충돌로 건너뜁니다(동시 배치 실행 또는 예기치 못한 제약 위반일 수 있음). userId={}, policyId={}",
                        user.getId(), policy.getId(), e);
            }
        }
    }

    private record BookmarkKey(Long userId, Long policyId) {
    }
}
