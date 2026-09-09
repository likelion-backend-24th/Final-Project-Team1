package com.team1.reservation.reservation.service;

import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.round.repository.RoundRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예약 하나를 만료시키는 단위 트랜잭션(#77).
 */
@Component
public class ReservationExpiryWriter {

    private final ReservationRepository reservations;
    private final RoundRepository rounds;

    public ReservationExpiryWriter(ReservationRepository reservations, RoundRepository rounds) {
        this.reservations = reservations;
        this.rounds = rounds;
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
}
