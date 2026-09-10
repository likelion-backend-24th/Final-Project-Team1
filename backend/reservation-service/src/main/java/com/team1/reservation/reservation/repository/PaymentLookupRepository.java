package com.team1.reservation.reservation.repository;

import com.team1.payment.PaymentTransaction;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;

/*
 예약 목록의 환불 상태를 파생하려면 결제 행을 일괄로 읽어야 한다.*/
public interface PaymentLookupRepository extends Repository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByRefIdIn(Collection<Long> refIds);
}
