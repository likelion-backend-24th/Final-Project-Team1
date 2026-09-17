package com.team1.recommendation.notification.service;

import com.team1.recommendation.common.ApiException;
import com.team1.recommendation.common.ErrorCode;
import com.team1.recommendation.notification.dto.NotificationItem;
import com.team1.recommendation.notification.dto.NotificationListResponse;
import com.team1.recommendation.notification.entity.Notification;
import com.team1.recommendation.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long userId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Slice<Notification> slice = unreadOnly
                ? repository.findByUserIdAndIsReadFalseOrderByCreatedAtDescIdDesc(userId, pageable)
                : repository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);

        List<NotificationItem> items = slice.getContent().stream()
                .map(NotificationItem::from)
                .toList();
        return new NotificationListResponse(items, slice.hasNext());
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "notification not found: " + notificationId));
        if (!notification.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "not your notification");
        }
        notification.markRead();
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId);
    }

    /**
     * 예약 확정 알림을 만든다. 같은 예약으로 다시 불려도 알림은 1건이다.
     *
     * <p>일부러 트랜잭션을 걸지 않는다. 동시에 두 번 들어와 유일 제약에 걸리면 저장만 실패시키고
     * 삼켜야 하는데, 바깥 트랜잭션이 있으면 그 실패가 전체를 rollback-only 로 만든다.
     */
    public void notifyReservationConfirmed(Long userId, Long expoId, Long reservationId, String reservationNo) {
        if (repository.existsByUserIdAndDedupKey(userId, Notification.reservationConfirmedKey(reservationId))) {
            return;
        }
        try {
            repository.saveAndFlush(Notification.reservationConfirmed(
                    userId, expoId, reservationId, reservationConfirmedMessage(reservationNo), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            log.info("reservation notification already exists userId={} reservationId={}", userId, reservationId);
        }
    }

    private static String reservationConfirmedMessage(String reservationNo) {
        String no = (reservationNo == null || reservationNo.isBlank()) ? "" : " (예약번호 " + reservationNo + ")";
        return "예약이 확정되었어요" + no + ". 내 예약에서 QR 티켓을 확인하세요.";
    }
}
