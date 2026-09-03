package com.team1.reservation.round.repository;

import com.team1.reservation.round.entity.Round;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RoundRepository extends JpaRepository<Round, Long> {

    List<Round> findByExpoIdOrderByStartsAtAsc(Long expoId);

    boolean existsByExpoId(Long expoId);


    @Query("select r.expoId from Round r group by r.expoId having max(r.endsAt) < :before order by r.expoId")
    List<Long> findExpoIdsWithAllRoundsEndedBefore(@Param("before") Instant before, Pageable pageable);
}
