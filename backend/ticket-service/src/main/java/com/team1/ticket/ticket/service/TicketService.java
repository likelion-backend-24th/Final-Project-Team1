package com.team1.ticket.ticket.service;

import com.team1.ticket.ticket.dto.IssueTicketsRequest;
import com.team1.ticket.ticket.dto.IssuedTicketsResponse;
import com.team1.ticket.ticket.dto.TicketView;
import com.team1.ticket.ticket.entity.Ticket;
import com.team1.ticket.ticket.entity.TicketStatus;
import com.team1.ticket.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final Clock clock;

    public TicketService(TicketRepository ticketRepository, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.clock = clock;
    }

    // 예약 확정 시 인원수(headcount)만큼 티켓 발급. 티켓당 QR(체크인 토큰) 1개.
    // 멱등: 같은 예약으로 이미 발급된 티켓이 있으면 재발급하지 않고 기존 것을 반환한다.
    @Transactional
    public IssuedTicketsResponse issue(IssueTicketsRequest request) {
        List<Ticket> existing = ticketRepository.findByReservationId(request.reservationId());
        if (!existing.isEmpty()) {
            return toResponse(request.reservationId(), existing);
        }

        Instant now = clock.instant();
        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < request.headcount(); i++) {
            tickets.add(Ticket.issue(
                    request.reservationId(),
                    request.expoId(),
                    request.roundId(),
                    request.userId(),
                    newToken(),
                    now));
        }
        ticketRepository.saveAll(tickets);
        return toResponse(request.reservationId(), tickets);
    }

    // 예약 취소 통지 → 해당 예약의 티켓 무효화. 이미 사용(USED)된 티켓은 건드리지 않는다.
    // 발급 전이거나 이미 취소됐어도 멱등적으로 성공한다.
    @Transactional
    public void revokeByReservation(Long reservationId) {
        for (Ticket ticket : ticketRepository.findByReservationId(reservationId)) {
            if (ticket.getStatus() == TicketStatus.ISSUED) {
                ticket.cancel();
            }
        }
    }

    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private IssuedTicketsResponse toResponse(Long reservationId, List<Ticket> tickets) {
        return new IssuedTicketsResponse(
                reservationId,
                tickets.stream().map(TicketView::from).toList());
    }
}
