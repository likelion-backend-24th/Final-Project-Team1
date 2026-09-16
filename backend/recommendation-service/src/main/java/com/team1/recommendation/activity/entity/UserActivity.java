package com.team1.recommendation.activity.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_activities")
public class UserActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expo_id", nullable = false)
    private Long expoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected UserActivity() {}

    public static UserActivity of(Long userId, Long expoId, EventType eventType, LocalDateTime now) {
        UserActivity a = new UserActivity();
        a.userId = userId;
        a.expoId = expoId;
        a.eventType = eventType;
        a.occurredAt = now;
        return a;
    }
}
