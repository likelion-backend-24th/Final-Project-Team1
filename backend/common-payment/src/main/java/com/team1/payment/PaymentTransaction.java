package com.team1.payment;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "payment_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction {

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
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PaymentTransaction create(Long refId, String paymentId, Integer amount) {
        PaymentTransaction p = new PaymentTransaction();
        p.refId = refId;
        p.paymentId = paymentId;
        p.amount = amount;
        p.status = PaymentStatus.PENDING;
        Instant now = Instant.now();
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    public void markPaid(String pgTransactionId, String pgResponseCode) {
        this.pgTransactionId = pgTransactionId;
        this.pgResponseCode = pgResponseCode;
        this.status = PaymentStatus.PAID;
        this.paidAt = Instant.now();
        this.updatedAt = this.paidAt;
    }

    public void markFailed(String pgResponseCode, String failureReason) {
        this.pgResponseCode = pgResponseCode;
        this.failureReason = failureReason;
        this.status = PaymentStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public void markCancelled() {
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.updatedAt = this.cancelledAt;
    }

    public void markRefundFailed(String failureReason) {
        this.failureReason = failureReason;
        this.status = PaymentStatus.REFUND_FAILED;
        this.updatedAt = Instant.now();
    }
}
