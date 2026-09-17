package com.team1.recommendation.notification.repository;

import com.team1.recommendation.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Slice<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
    Slice<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    boolean existsByUserIdAndDedupKey(Long userId, String dedupKey);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.userId = :userId and n.isRead = false")
    int markAllRead(@Param("userId") Long userId);
}
