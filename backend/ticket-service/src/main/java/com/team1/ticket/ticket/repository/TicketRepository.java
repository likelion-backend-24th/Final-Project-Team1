package com.team1.ticket.ticket.repository;

import com.team1.ticket.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // 예약당 티켓 1건이므로 단건 조회. 멱등 판단·무효화에 사용한다.
    Optional<Ticket> findByReservationId(Long reservationId);

    // 체크인: QR 로 스캔한 체크인 토큰으로 티켓을 찾는다.
    Optional<Ticket> findByCheckinToken(String checkinToken);
}
