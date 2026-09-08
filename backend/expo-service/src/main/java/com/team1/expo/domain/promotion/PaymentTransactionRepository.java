package com.team1.expo.domain.promotion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByRefId(Long promotionId);
    Optional<PaymentTransaction> findByPgTransactionId(String pgTransactionId);
}
