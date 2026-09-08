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


    /**
     * 정원 차감. 남은 자리가 부족하면 WHERE 가 걸러내므로 갱신 행 수가 0 이 된다.
     * <p>락을 잡지 않고 DB 가 원자적으로 판정한다. 같은 회차에 요청이 동시에 몰려도
     * UPDATE 는 행 락 위에서 하나씩 직렬로 평가되므로 capacity 를 넘길 수 없다.
     * <p>검토했던 대안:
     * <ul>
     *   <li>비관적 락({@code SELECT ... FOR UPDATE}) - 같은 회차 요청이 전부 직렬화돼
     *       인기 회차에서 처리량이 떨어진다.</li>
     *   <li>낙관적 락({@code @Version}) - 충돌 시 재시도 로직이 필요하고, 경합이 심할수록
     *       재시도가 늘어 오히려 느려진다.</li>
     * </ul>
     * @return 갱신된 행 수. 1 이면 차감 성공, 0 이면 정원 초과
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Round r set r.reservedCount = r.reservedCount + :headcount "
            + "where r.id = :roundId and r.reservedCount + :headcount <= r.capacity")
    int reserve(@Param("roundId") Long roundId, @Param("headcount") int headcount);


    /**
     * 정원 반환(#77 만료, #83 취소). 0 미만으로 내려가지 않는 경우에만 갱신한다 -
     * 같은 예약에 대해 반환이 두 번 호출돼도 두 번째는 갱신 행 수 0 으로 걸러진다.
     * @return 갱신된 행 수. 1 이면 반환 성공, 0 이면 이미 반환됐거나 값이 어긋난 상태
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Round r set r.reservedCount = r.reservedCount - :headcount "
            + "where r.id = :roundId and r.reservedCount - :headcount >= 0")
    int release(@Param("roundId") Long roundId, @Param("headcount") int headcount);
}
