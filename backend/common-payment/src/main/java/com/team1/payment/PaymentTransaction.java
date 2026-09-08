package com.team1.payment;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "payment_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ref_id", nullable = false, unique = true)
    private Long refId;

    @Column(name = "payment_id", nullable = false, unique = true, length = 100)
    private String paymentId;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "pg_transaction_id", length = 100)
    private String pgTransactionId;

    @Column(name = "pg_response_code", length = 50)
    private String pgResponseCode;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static PaymentTransaction create(Long refId, String paymentId, Integer amount) {
        PaymentTransaction p = new PaymentTransaction();
        p.refId = refId;
        p.paymentId = paymentId;
        p.amount = amount;
        p.status = PaymentStatus.PENDING;
        LocalDateTime now = LocalDateTime.now(ZONE);
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    public void markPaid(String pgTransactionId, String pgResponseCode) {
        this.pgTransactionId = pgTransactionId;
        this.pgResponseCode = pgResponseCode;
        this.status = PaymentStatus.PAID;
        this.paidAt = LocalDateTime.now(ZONE);
        this.updatedAt = this.paidAt;
    }

    public void markFailed(String pgResponseCode, String failureReason) {
        this.pgResponseCode = pgResponseCode;
        this.failureReason = failureReason;
        this.status = PaymentStatus.FAILED;
        this.updatedAt = LocalDateTime.now(ZONE);
    }

    public void markCancelled() {
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now(ZONE);
        this.updatedAt = this.cancelledAt;
    }

    public void markRefundFailed(String failureReason) {
        this.failureReason = failureReason;
        this.status = PaymentStatus.REFUND_FAILED;
        this.updatedAt = LocalDateTime.now(ZONE);
    }
}
