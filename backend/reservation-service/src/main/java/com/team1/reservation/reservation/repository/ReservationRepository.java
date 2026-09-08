package com.team1.reservation.reservation.repository;

import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 같은 회차에 아직 살아 있는 예약이 있는지 본다.
     * <p>{@code UNIQUE (round_id, user_id)} 를 걸지 않은 이유가 여기 있다. UNIQUE 를 걸면
     * 취소하거나 만료된 예약까지 자리를 차지해 그 회원은 다시 예약할 수 없게 된다.
     * 유효 상태(PENDING·CONFIRMED)만 골라 조회해야 재예약이 열린다.
     */
    boolean existsByRoundIdAndUserIdAndStatusIn(Long roundId, Long userId,
                                                Collection<ReservationStatus> statuses);

    List<Reservation> findByRoundIdAndStatusIn(Long roundId, Collection<ReservationStatus> statuses);
}
