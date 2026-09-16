package com.team1.reservation.reservation.repository;

import com.team1.reservation.reservation.entity.TicketDispatch;
import com.team1.reservation.reservation.entity.TicketDispatchStatus;
import com.team1.reservation.reservation.entity.TicketDispatchType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TicketDispatchRepository extends JpaRepository<TicketDispatch, Long> {

    Optional<TicketDispatch> findByReservationIdAndType(Long reservationId, TicketDispatchType type);

    /** 재시도 대상. 오래 밀린 것부터 집는다. */
    @Query("select d from TicketDispatch d "
            + "where d.status = com.team1.reservation.reservation.entity.TicketDispatchStatus.PENDING "
            + "and d.nextAttemptAt <= :now order by d.nextAttemptAt asc")
    List<TicketDispatch> findDue(@Param("now") Instant now, Pageable pageable);

    /** 자동 회수를 포기한 건. 오래된 것부터 - 구멍이 오래 열려 있던 순서다. */
    List<TicketDispatch> findByStatusOrderByUpdatedAtAsc(TicketDispatchStatus status, Pageable pageable);

    List<TicketDispatch> findByStatusAndTypeOrderByUpdatedAtAsc(TicketDispatchStatus status,
                                                                TicketDispatchType type,
                                                                Pageable pageable);
}
