package com.team1.reservation.reservation.repository;

import com.team1.reservation.reservation.entity.TicketDispatch;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TicketDispatchRepository extends JpaRepository<TicketDispatch, Long> {

    Optional<TicketDispatch> findByReservationId(Long reservationId);

    /** 재시도 대상. 오래 밀린 것부터 집는다. */
    @Query("select d from TicketDispatch d "
            + "where d.status = com.team1.reservation.reservation.entity.TicketDispatchStatus.PENDING "
            + "and d.nextAttemptAt <= :now order by d.nextAttemptAt asc")
    List<TicketDispatch> findDue(@Param("now") Instant now, Pageable pageable);
}
