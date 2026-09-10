package com.team1.payment;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByRefId(Long refId);
    Optional<PaymentTransaction> findByPaymentId(String paymentId);

    /** 환불 재시도 대상(#125). REFUND_FAILED 이고 상한 안 넘겼고 재시도 시각이 된 것만. */
    @Query("select p from PaymentTransaction p "
            + "where p.status = com.team1.payment.PaymentStatus.REFUND_FAILED "
            + "and p.attempts < :maxAttempts and p.nextAttemptAt <= :now "
            + "order by p.nextAttemptAt asc")

    List<PaymentTransaction> findDueForRefundRetry(@Param("maxAttempts") int maxAttempts,
                                                   @Param("now") Instant now,
                                                   Pageable pageable);
}
