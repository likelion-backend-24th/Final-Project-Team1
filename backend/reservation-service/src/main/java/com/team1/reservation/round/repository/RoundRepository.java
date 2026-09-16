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

    // 삭제된 회차는 "미래의 행동" 을 받는 경로에서 전부 빠진다(S9-3).
    List<Round> findByExpoIdAndDeletedAtIsNullOrderByStartsAtAsc(Long expoId);

    // 박람회 공개 조건(#24). 삭제된 회차만 있는 박람회가 공개되면 안 된다.
    boolean existsByExpoIdAndDeletedAtIsNull(Long expoId);

    // 마지막 살아있는 회차인지 판정한다. 1 이면 지금 지우는 그 회차가 마지막이다.
    long countByExpoIdAndDeletedAtIsNull(Long expoId);


    // 삭제된 회차가 max(ends_at) 판정을 왜곡하면 안 된다.
    @Query("select r.expoId from Round r where r.deletedAt is null "
            + "group by r.expoId having max(r.endsAt) < :before order by r.expoId")
    List<Long> findExpoIdsWithAllRoundsEndedBefore(@Param("before") Instant before, Pageable pageable);



    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Round r set r.reservedCount = r.reservedCount + :headcount "
            + "where r.id = :roundId and r.deletedAt is null "
            + "and r.reservedCount + :headcount <= r.capacity")
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
            + "where r.id = :roundId and r.reservedCount = 0 and r.deletedAt is null")
    int updateIfNoReservation(@Param("roundId") Long roundId,
                              @Param("startsAt") Instant startsAt,
                              @Param("endsAt") Instant endsAt,
                              @Param("capacity") int capacity,
                              @Param("fee") int fee);


    /**
     * 활성 예약이 없을 때만 삭제한다(S9-3). 수정과 같은 이유로 조건을 UPDATE 안에 둔다.
     * 이미 삭제된 회차는 0 행이 되어 호출부가 멱등으로 처리한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Round r set r.deletedAt = :now "
            + "where r.id = :roundId and r.reservedCount = 0 and r.deletedAt is null")
    int softDeleteIfNoReservation(@Param("roundId") Long roundId, @Param("now") Instant now);
}
