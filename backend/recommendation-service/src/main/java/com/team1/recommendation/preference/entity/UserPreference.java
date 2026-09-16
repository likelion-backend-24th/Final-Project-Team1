package com.team1.recommendation.preference.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_interests")
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PreferenceType type;

    @Column(nullable = false, length = 100)
    private String value;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected UserPreference() {}

    public static UserPreference of(Long userId, PreferenceType type, String value, LocalDateTime now) {
        UserPreference up = new UserPreference();
        up.userId = userId;
        up.type = type;
        up.value = value;
        up.createdAt = now;
        return up;
    }

    public Long getUserId() { return userId; }
    public PreferenceType getType() { return type; }
    public String getValue() { return value; }
}
