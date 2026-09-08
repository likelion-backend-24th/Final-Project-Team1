package com.team1.expo.domain.promotion;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.LocalDateTime;

@Entity
@Table(name = "expo_promotions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpoPromotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long expoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExpoPromotionStatus status;

    @Column(nullable = false)
    private int amount;

    private LocalDateTime paidAt;

    private LocalDateTime cancelledAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static ExpoPromotion create(Long expoId, int amount, Clock clock) {
        ExpoPromotion p = new ExpoPromotion();
        p.expoId = expoId;
        p.status = ExpoPromotionStatus.PENDING;
        p.amount = amount;
        p.createdAt = LocalDateTime.now(clock);
        return p;
    }

    public void confirm(Clock clock) {
        this.status = ExpoPromotionStatus.ACTIVE;
        this.paidAt = LocalDateTime.now(clock);
    }

    public void cancel(Clock clock) {
        this.status = ExpoPromotionStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now(clock);
    }
}
