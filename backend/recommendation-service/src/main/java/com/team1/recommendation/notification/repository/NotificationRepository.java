package com.team1.recommendation.notification.repository;

import com.team1.recommendation.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Slice<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Slice<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
