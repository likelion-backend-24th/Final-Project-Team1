package com.team1.recommendation.notification.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Notification() {}

    public static Notification of(Long userId, Long expoId, String message, LocalDateTime now) {
        Notification n = new Notification();
        n.userId = userId;
        n.expoId = expoId;
        n.message = message;
        n.isRead = false;
        n.createdAt = now;
        return n;
    }

    public void markRead() { this.isRead = true; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getExpoId() { return expoId; }
    public String getMessage() { return message; }
    public boolean isRead() { return isRead; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
