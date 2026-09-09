package com.team1.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByRefId(Long refId);
    Optional<PaymentTransaction> findByPaymentId(String paymentId);
}
