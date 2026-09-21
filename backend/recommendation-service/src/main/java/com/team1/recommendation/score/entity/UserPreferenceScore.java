package com.team1.recommendation.score.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_preference_scores",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "tag_value", "source"}))
public class UserPreferenceScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tag_value", nullable = false, length = 100)
    private String tagValue;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ScoreSource source;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserPreferenceScore() {}

    public static UserPreferenceScore of(Long userId, String tagValue, double score, ScoreSource source, LocalDateTime now) {
        UserPreferenceScore s = new UserPreferenceScore();
        s.userId = userId;
        s.tagValue = tagValue;
        s.score = BigDecimal.valueOf(score);
        s.source = source;
        s.updatedAt = now;
        return s;
    }

    public Long getUserId() { return userId; }
    public String getTagValue() { return tagValue; }
    public double getScore() { return score.doubleValue(); }
    public ScoreSource getSource() { return source; }

    public void updateScore(double newScore, LocalDateTime now) {
        this.score = BigDecimal.valueOf(newScore);
        this.updatedAt = now;
    }
}
