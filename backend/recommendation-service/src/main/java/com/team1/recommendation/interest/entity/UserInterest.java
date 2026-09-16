package com.team1.recommendation.interest.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_interests")
public class UserInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InterestType type;

    @Column(nullable = false, length = 100)
    private String value;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected UserInterest() {}

    public static UserInterest of(Long userId, InterestType type, String value, LocalDateTime now) {
        UserInterest ui = new UserInterest();
        ui.userId = userId;
        ui.type = type;
        ui.value = value;
        ui.createdAt = now;
        return ui;
    }

    public Long getUserId() { return userId; }
    public InterestType getType() { return type; }
    public String getValue() { return value; }
}
