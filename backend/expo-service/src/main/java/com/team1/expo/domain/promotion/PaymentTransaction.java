package com.team1.expo.domain.promotion;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long refId;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentTransactionStatus status;

    @Column(length = 100)
    private String pgTransactionId;

    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;

    @Column(length = 500)
    private String failureReason;

    @Column(length = 50)
    private String pgResponseCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static PaymentTransaction pending(Long promotionId, int amount, String pgTransactionId, Clock clock) {
        PaymentTransaction t = new PaymentTransaction();
        t.refId = promotionId;
        t.amount = amount;
        t.status = PaymentTransactionStatus.PENDING;
        t.pgTransactionId = pgTransactionId;
        t.createdAt = LocalDateTime.now(clock);
        t.updatedAt = t.createdAt;
        return t;
    }

    public void markPaid(Clock clock) {
        this.status = PaymentTransactionStatus.PAID;
        this.paidAt = LocalDateTime.now(clock);
        this.updatedAt = this.paidAt;
    }

    public void markFailed(String reason, String code, Clock clock) {
        this.status = PaymentTransactionStatus.FAILED;
        this.failureReason = reason;
        this.pgResponseCode = code;
        this.updatedAt = LocalDateTime.now(clock);
    }

    public void markCancelled(Clock clock) {
        this.status = PaymentTransactionStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now(clock);
        this.updatedAt = this.cancelledAt;
    }

    public void markRefundFailed(String reason, Clock clock) {
        this.status = PaymentTransactionStatus.REFUND_FAILED;
        this.failureReason = reason;
        this.updatedAt = LocalDateTime.now(clock);
    }
}
