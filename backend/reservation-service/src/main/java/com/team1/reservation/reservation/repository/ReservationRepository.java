package com.team1.reservation.reservation.repository;

import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.dto.RoundStatusHeadcount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 같은 회차에 아직 살아 있는 예약이 있는지 본다.
     * 유효 상태(PENDING·CONFIRMED)만 골라 조회해야 재예약이 열린다.
     */
    boolean existsByRoundIdAndUserIdAndStatusIn(Long roundId, Long userId,
                                                Collection<ReservationStatus> statuses);

    List<Reservation> findByRoundIdAndStatusIn(Long roundId, Collection<ReservationStatus> statuses);

    /**
     * 회차·상태별 예약 인원 합(#84 예약 현황). 정원은 rounds 에 있으므로 Service 가 합친다.
     */
    @Query("select new com.team1.reservation.reservation.dto.RoundStatusHeadcount("
            + "r.roundId, r.status, sum(r.headcount)) "
            + "from Reservation r where r.expoId = :expoId group by r.roundId, r.status")
    List<RoundStatusHeadcount> sumHeadcountByRoundAndStatus(@Param("expoId") Long expoId);


    /** 박람회 전체 명단(#84). 엑셀 다운로드는 회차를 나누지 않고 한 번에 받아간다. */
    List<Reservation> findByExpoIdAndStatusInOrderByRoundIdAscCreatedAtAsc(
            Long expoId, Collection<ReservationStatus> statuses);


    List<Reservation> findByExpoIdAndRoundIdAndStatusInOrderByCreatedAtAsc(
            Long expoId, Long roundId, Collection<ReservationStatus> statuses);
}
