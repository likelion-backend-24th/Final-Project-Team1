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

    /** 종류와 무관하게 그 박람회 알림이 하나라도 있으면 true. 이미 예약한 박람회는 추천하지 않는다. */
    boolean existsByUserIdAndExpoId(Long userId, Long expoId);

    boolean existsByUserIdAndDedupKey(Long userId, String dedupKey);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.userId = :userId and n.isRead = false")
    int markAllRead(@Param("userId") Long userId);
}
