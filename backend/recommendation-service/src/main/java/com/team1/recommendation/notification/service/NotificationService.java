package com.team1.recommendation.notification.service;

import com.team1.recommendation.common.ApiException;
import com.team1.recommendation.common.ErrorCode;
import com.team1.recommendation.notification.dto.NotificationItem;
import com.team1.recommendation.notification.dto.NotificationListResponse;
import com.team1.recommendation.notification.entity.Notification;
import com.team1.recommendation.notification.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long userId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Slice<Notification> slice = unreadOnly
                ? repository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
                : repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        List<NotificationItem> items = slice.getContent().stream()
                .map(NotificationItem::from)
                .toList();
        return new NotificationListResponse(items, slice.hasNext());
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
}
