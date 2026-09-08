package com.team1.ticket.ticket.service;

import com.team1.ticket.ticket.dto.IssueTicketsRequest;
import com.team1.ticket.ticket.dto.IssuedTicketsResponse;
import com.team1.ticket.ticket.entity.Ticket;
import com.team1.ticket.ticket.entity.TicketStatus;
import com.team1.ticket.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-07T02:00:00Z");
    private static final long RESERVATION_ID = 123L;

    private TicketRepository tickets;
    private TicketService service;

    @BeforeEach
    void setUp() {
        tickets = mock(TicketRepository.class);
        service = new TicketService(tickets, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private IssueTicketsRequest request(int headcount) {
        return new IssueTicketsRequest(RESERVATION_ID, 10L, 45L, 77L, headcount);
    }

    @Test
    @DisplayName("발급: 인원수(headcount)만큼 티켓이 생성되고 각 티켓은 ISSUED · 발급시각 고정")
    @SuppressWarnings("unchecked")
    void issuesOneTicketPerHeadcount() {
        when(tickets.findByReservationId(RESERVATION_ID)).thenReturn(List.of());

        IssuedTicketsResponse response = service.issue(request(3));

        assertThat(response.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(response.tickets()).hasSize(3);

        ArgumentCaptor<List<Ticket>> captor = ArgumentCaptor.forClass(List.class);
        verify(tickets).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3).allSatisfy(ticket -> {
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ISSUED);
            assertThat(ticket.getIssuedAt()).isEqualTo(NOW);
            assertThat(ticket.getReservationId()).isEqualTo(RESERVATION_ID);
            assertThat(ticket.getExpoId()).isEqualTo(10L);
            assertThat(ticket.getRoundId()).isEqualTo(45L);
            assertThat(ticket.getUserId()).isEqualTo(77L);
        });
    }

    @Test
    @DisplayName("발급: 체크인 토큰은 티켓당 1개이며 서로 다르다 (QR 1개 = 티켓 1개)")
    @SuppressWarnings("unchecked")
    void issuesDistinctTokenPerTicket() {
        when(tickets.findByReservationId(RESERVATION_ID)).thenReturn(List.of());

        service.issue(request(3));

        ArgumentCaptor<List<Ticket>> captor = ArgumentCaptor.forClass(List.class);
        verify(tickets).saveAll(captor.capture());
        List<String> tokens = captor.getValue().stream().map(Ticket::getCheckinToken).toList();
        assertThat(tokens).hasSize(3).doesNotContainNull().doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("멱등: 같은 예약으로 이미 발급된 티켓이 있으면 재발급하지 않고 기존 티켓을 반환한다")
    void idempotentWhenAlreadyIssued() {
        List<Ticket> existing = List.of(
                Ticket.issue(RESERVATION_ID, 10L, 45L, 77L, "tok-1", NOW),
                Ticket.issue(RESERVATION_ID, 10L, 45L, 77L, "tok-2", NOW));
        when(tickets.findByReservationId(RESERVATION_ID)).thenReturn(existing);

        // 재호출의 headcount(5)가 아니라 기존 발급분(2)을 그대로 돌려준다.
        IssuedTicketsResponse response = service.issue(request(5));

        assertThat(response.tickets()).hasSize(2);
        verify(tickets, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("무효화: 예약의 ISSUED 티켓을 CANCELLED 로 전이한다")
    void revokeCancelsIssuedTickets() {
        Ticket a = Ticket.issue(RESERVATION_ID, 10L, 45L, 77L, "tok-1", NOW);
        Ticket b = Ticket.issue(RESERVATION_ID, 10L, 45L, 77L, "tok-2", NOW);
        when(tickets.findByReservationId(RESERVATION_ID)).thenReturn(List.of(a, b));

        service.revokeByReservation(RESERVATION_ID);

        assertThat(a.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(b.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }

    @Test
    @DisplayName("무효화 멱등: 발급된 티켓이 없어도 예외 없이 성공한다")
    void revokeIsIdempotentWhenNoTickets() {
        when(tickets.findByReservationId(RESERVATION_ID)).thenReturn(List.of());

        service.revokeByReservation(RESERVATION_ID);
    }

    @Test
    @DisplayName("무효화 멱등: 이미 취소된 티켓은 그대로 CANCELLED 로 둔다")
    void revokeKeepsAlreadyCancelled() {
        Ticket cancelled = Ticket.issue(RESERVATION_ID, 10L, 45L, 77L, "tok-1", NOW);
        cancelled.cancel();
        when(tickets.findByReservationId(RESERVATION_ID)).thenReturn(List.of(cancelled));

        service.revokeByReservation(RESERVATION_ID);

        assertThat(cancelled.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }
}
