package com.team1.recommendation.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expo_id", nullable = false)
    private Long expoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    /** (user_id, dedup_key) 가 유일하다. 같은 사건으로 알림이 두 번 생기지 않게 한다. */
    @Column(name = "dedup_key", nullable = false, length = 100)
    private String dedupKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {}

    /** 취향 기반 추천 알림. 같은 회원·박람회 조합은 한 번만 만들어진다. */
    public static Notification recommendation(Long userId, Long expoId, String message, Instant now) {
        return create(userId, expoId, NotificationType.RECOMMENDATION,
                recommendationKey(expoId), message, now);
    }

    /** 예약 확정 알림. 예약 하나에 한 번만 만들어진다. */
    public static Notification reservationConfirmed(Long userId, Long expoId, Long reservationId,
                                                    String message, Instant now) {
        return create(userId, expoId, NotificationType.RESERVATION_CONFIRMED,
                reservationConfirmedKey(reservationId), message, now);
    }

    public static String recommendationKey(Long expoId) {
        return NotificationType.RECOMMENDATION.name() + ":" + expoId;
    }

    public static String reservationConfirmedKey(Long reservationId) {
        return NotificationType.RESERVATION_CONFIRMED.name() + ":" + reservationId;
    }

    private static Notification create(Long userId, Long expoId, NotificationType type,
                                       String dedupKey, String message, Instant now) {
        Notification n = new Notification();
        n.userId = userId;
        n.expoId = expoId;
        n.type = type;
        n.dedupKey = dedupKey;
        n.message = message;
        n.isRead = false;
        n.createdAt = now;
        return n;
    }

    public void markRead() { this.isRead = true; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getExpoId() { return expoId; }
    public NotificationType getType() { return type; }
    public String getDedupKey() { return dedupKey; }
    public String getMessage() { return message; }
    public boolean isRead() { return isRead; }
    public Instant getCreatedAt() { return createdAt; }
}
