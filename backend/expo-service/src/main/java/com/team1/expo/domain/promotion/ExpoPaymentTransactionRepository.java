package com.team1.expo.domain.promotion;

import com.team1.payment.PaymentStatus;
import com.team1.payment.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExpoPaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByRefId(Long promotionId);
    Optional<PaymentTransaction> findByPaymentId(String paymentId);
    List<PaymentTransaction> findByStatusInAndUpdatedAtBetween(Collection<PaymentStatus> statuses, Instant from, Instant to);
}
