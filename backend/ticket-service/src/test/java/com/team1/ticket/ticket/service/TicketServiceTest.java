package com.team1.ticket.ticket.service;

import com.team1.ticket.common.ApiException;
import com.team1.ticket.common.ErrorCode;
import com.team1.ticket.ticket.dto.IssueTicketsRequest;
import com.team1.ticket.ticket.dto.IssuedTicketResponse;
import com.team1.ticket.ticket.dto.TicketDetailResponse;
import com.team1.ticket.ticket.entity.Ticket;
import com.team1.ticket.ticket.entity.TicketStatus;
import com.team1.ticket.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @DisplayName("발급: 예약당 티켓 1건만 만들고, 인원수는 headcount 로 저장한다 (코드 1개 = N명분)")
    void issuesSingleTicketPerReservation() {
        when(tickets.findByReservationId(RESERVATION_ID)).thenReturn(Optional.empty());
        when(tickets.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        IssuedTicketResponse response = service.issue(request(3));

        assertThat(response.issuedAt()).isEqualTo(NOW);
        assertThat(response.checkinToken()).isNotBlank();

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets, times(1)).save(captor.capture());
        Ticket saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TicketStatus.ISSUED);
        assertThat(saved.getHeadcount()).isEqualTo(3);
        assertThat(saved.getReservationId()).isEqualTo(RESERVATION_ID);
        assertThat(saved.getIssuedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("멱등: 같은 예약으로 이미 발급된 티켓이 있으면 재발급하지 않고 기존 것을 반환한다")
    void idempotentReturnsExistingWithoutSaving() {
        Ticket existing = Ticket.issue(RESERVATION_ID, 10L, 45L, 77L, 3, "tok-1", NOW);
        when(tickets.findByReservationId(RESERVATION_ID)).thenReturn(Optional.of(existing));

        IssuedTicketResponse response = service.issue(request(3));

        assertThat(response.checkinToken()).isEqualTo("tok-1");
        verify(tickets, never()).save(any(Ticket.class));
    }

    @Test
    @DisplayName("멱등(경합): 동시 재시도로 reservation_id UNIQUE 위반이 나면 기존 티켓을 반환한다")
    void returnsExistingOnUniqueViolation() {
        Ticket existing = Ticket.issue(RESERVATION_ID, 10L, 45L, 77L, 3, "tok-1", NOW);
        when(tickets.findByReservationId(RESERVATION_ID))
                .thenReturn(Optional.empty())        // 최초 조회 → 없음
                .thenReturn(Optional.of(existing));  // 저장 충돌 후 재조회 → 기존 반환
        when(tickets.save(any(Ticket.class)))
                .thenThrow(new DataIntegrityViolationException("uk_tickets_reservation_id"));

        IssuedTicketResponse response = service.issue(request(3));

        assertThat(response.checkinToken()).isEqualTo("tok-1");
    }

    @Test
    @DisplayName("무효화: 예약의 ISSUED 티켓을 CANCELLED 로 전이한다")
    void revokeCancelsIssuedTicket() {
        Ticket ticket = Ticket.issue(RESERVATION_ID, 10L, 45L, 77L, 2, "tok-1", NOW);
        when(tickets.findByReservationId(RESERVATION_ID)).thenReturn(Optional.of(ticket));

        service.revokeByReservation(RESERVATION_ID);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }

    @Test
    @DisplayName("무효화 멱등: 발급된 티켓이 없어도 예외 없이 성공한다")
    void revokeIsIdempotentWhenNoTicket() {
        when(tickets.findByReservationId(RESERVATION_ID)).thenReturn(Optional.empty());

        service.revokeByReservation(RESERVATION_ID);
    }

    @Test
    @DisplayName("무효화 멱등: 이미 취소된 티켓은 그대로 CANCELLED 로 둔다")
    void revokeKeepsAlreadyCancelled() {
        Ticket cancelled = Ticket.issue(RESERVATION_ID, 10L, 45L, 77L, 2, "tok-1", NOW);
        cancelled.cancel();
        when(tickets.findByReservationId(RESERVATION_ID)).thenReturn(Optional.of(cancelled));

        service.revokeByReservation(RESERVATION_ID);

        assertThat(cancelled.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }

    @Test
    @DisplayName("예약별 조회: 티켓이 있으면 status 를 포함해 반환한다 (화면 분기용)")
    void getByReservationReturnsDetailWithStatus() {
        Ticket ticket = Ticket.issue(RESERVATION_ID, 10L, 45L, 77L, 2, "tok-1", NOW);
        when(tickets.findByReservationId(RESERVATION_ID)).thenReturn(Optional.of(ticket));

        TicketDetailResponse response = service.getByReservation(RESERVATION_ID);

        assertThat(response.checkinToken()).isEqualTo("tok-1");
        assertThat(response.status()).isEqualTo("ISSUED");
        assertThat(response.issuedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("예약별 조회: 티켓이 아직 없으면 404 (발급 중 — 진짜 장애와 구분)")
    void getByReservationThrowsNotFoundWhenAbsent() {
        when(tickets.findByReservationId(RESERVATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByReservation(RESERVATION_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
