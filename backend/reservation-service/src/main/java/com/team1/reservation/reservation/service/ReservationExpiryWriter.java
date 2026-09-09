package com.team1.reservation.reservation.service;

import com.team1.payment.PaymentApprovalResult;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.round.repository.RoundRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료 배치의 쓰기 단위 트랜잭션(#77).
 *
 * <p>스캔 루프와 클래스를 나눈 이유는 self-invocation 이다. 같은 Bean 안에서 부르면 Proxy 를
 * 거치지 않아 {@code @Transactional} 이 통째로 무시되고, 배치 하나가 실패할 때 이미 처리한
 * 나머지까지 같이 롤백된다.
 */
@Component
public class ReservationExpiryWriter {

    private final ReservationRepository reservations;
    private final RoundRepository rounds;
    private final ReservationPaymentService paymentTransition;

    public ReservationExpiryWriter(ReservationRepository reservations,
                                   RoundRepository rounds,
                                   ReservationPaymentService paymentTransition) {
        this.reservations = reservations;
        this.rounds = rounds;
        this.paymentTransition = paymentTransition;
    }

    /** 전이에 성공했으면 true. 결제 승인이 먼저 이겼으면 false 이고 정원은 건드리지 않는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(Long reservationId, Long roundId, int headcount) {
        if (reservations.expireIfPending(reservationId) == 0) {
            return false;
        }
        // 전이가 정확히 1회 일어났을 때만 여기 도달하므로 정원 반환도 정확히 1회다.
        rounds.release(roundId, headcount);
        return true;
    }

    /**
     * PG 재조회 결과를 예약에 반영한다(갈래 2). SUCCESS 면 확정+티켓 통지, 실패확정이면
     * 취소+정원 반환 — 둘 다 {@link ReservationPaymentService#applyOutcome} 이 이미 하는 일이라
     * 그대로 재사용한다. 확정 경로가 갈라지면 한쪽만 고치는 사고가 난다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean settle(Long reservationId, PaymentApprovalResult result) {
        Reservation reservation = reservations.findById(reservationId).orElseThrow();

        // 사용자의 확정 호출이나 웹훅이 먼저 이겼으면 아무것도 하지 않는다.
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            return false;
        }
        paymentTransition.applyOutcome(reservation, result);
        return true;
    }
}
