package com.team1.ticket.ticket.repository;

import com.team1.ticket.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByReservationId(Long reservationId);

    boolean existsByReservationId(Long reservationId);
}
