package com.team1.ticket.ticket.service;

import com.team1.ticket.ticket.dto.IssueTicketsRequest;
import com.team1.ticket.ticket.dto.IssuedTicketResponse;
import com.team1.ticket.ticket.entity.Ticket;
import com.team1.ticket.ticket.entity.TicketStatus;
import com.team1.ticket.ticket.repository.TicketRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;


@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final Clock clock;

    public TicketService(TicketRepository ticketRepository, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.clock = clock;
    }

    // 예약 확정 시 티켓 1건 발급(예약당 1건, API 계약 v3 #17). 코드는 1개지만 headcount 명분이다.
    // 멱등: 같은 예약으로 이미 발급된 티켓이 있으면 재발급하지 않고 기존 것을 반환한다.
    // 발급 통지가 fail-open(재시도 전제)이라, 동시 재호출로 UNIQUE 위반이 나도 기존 티켓을 반환한다.
    @Transactional
    public IssuedTicketResponse issue(IssueTicketsRequest request) {
        return ticketRepository.findByReservationId(request.reservationId())
                .map(IssuedTicketResponse::from)
                .orElseGet(() -> create(request));
    }

    private IssuedTicketResponse create(IssueTicketsRequest request) {
        Ticket ticket = Ticket.issue(
                request.reservationId(),
                request.expoId(),
                request.roundId(),
                request.userId(),
                request.headcount(),
                newToken(),
                clock.instant());
        try {
            return IssuedTicketResponse.from(ticketRepository.save(ticket));
        } catch (DataIntegrityViolationException raced) {
            // 동시 재시도가 reservation_id UNIQUE 를 동시에 뚫으려다 진 경우 → 기존 티켓 반환(멱등).
            return ticketRepository.findByReservationId(request.reservationId())
                    .map(IssuedTicketResponse::from)
                    .orElseThrow(() -> raced);
        }
    }

    // 예약 취소 통지 → 해당 예약의 티켓 무효화. 이미 사용(USED)된 티켓은 건드리지 않는다.
    // 발급 전이거나 이미 취소됐어도 멱등적으로 성공한다.
    @Transactional
    public void revokeByReservation(Long reservationId) {
        ticketRepository.findByReservationId(reservationId)
                .filter(ticket -> ticket.getStatus() == TicketStatus.ISSUED)
                .ifPresent(Ticket::cancel);
    }

    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
