package com.team1.reservation.round.repository;

import com.team1.reservation.round.entity.Round;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RoundRepository extends JpaRepository<Round, Long> {

    List<Round> findByExpoIdOrderByStartsAtAsc(Long expoId);

    boolean existsByExpoId(Long expoId);


    @Query("select r.expoId from Round r group by r.expoId having max(r.endsAt) < :before order by r.expoId")
    List<Long> findExpoIdsWithAllRoundsEndedBefore(@Param("before") Instant before, Pageable pageable);



    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Round r set r.reservedCount = r.reservedCount + :headcount "
            + "where r.id = :roundId and r.reservedCount + :headcount <= r.capacity")
    int reserve(@Param("roundId") Long roundId, @Param("headcount") int headcount);



    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Round r set r.reservedCount = r.reservedCount - :headcount "
            + "where r.id = :roundId and r.reservedCount - :headcount >= 0")
    int release(@Param("roundId") Long roundId, @Param("headcount") int headcount);


    /**
     * 활성 예약이 없을 때만 수정한다(S9-2). 읽어서 판단하면 그 사이에 들어온 예약을 놓치므로
     * 조건을 UPDATE 문 안에 둔다 - 예약 생성의 정원 차감(reserve)과 같은 모양이다.
     *
     * <p>reserved_count 는 reserve() 가 올리고 release() 가 내리므로 정확히 활성 예약 인원이다.
     * 예약이 모두 취소되면 0 으로 돌아와 자동으로 다시 수정 가능해진다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Round r set r.startsAt = :startsAt, r.endsAt = :endsAt, "
            + "r.capacity = :capacity, r.fee = :fee "
            + "where r.id = :roundId and r.reservedCount = 0")
    int updateIfNoReservation(@Param("roundId") Long roundId,
                              @Param("startsAt") Instant startsAt,
                              @Param("endsAt") Instant endsAt,
                              @Param("capacity") int capacity,
                              @Param("fee") int fee);
}
