package com.team1.ticket.ticket;

import com.team1.ticket.support.IntegrationTestSupport;
import com.team1.ticket.ticket.dto.IssueTicketsRequest;
import com.team1.ticket.ticket.dto.IssuedTicketResponse;
import com.team1.ticket.ticket.entity.Ticket;
import com.team1.ticket.ticket.repository.TicketRepository;
import com.team1.ticket.ticket.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 발급 멱등의 근거인 reservation_id UNIQUE 제약과 재호출 동작을 실DB로 검증.
class TicketIssueIdempotencyIntegrationTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-20T05:00:00Z");
    private static final long RESERVATION_ID = 123L;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketService ticketService;

    @BeforeEach
    void clean() {
        ticketRepository.deleteAll();
    }

    @Test
    @DisplayName("멱등: 같은 예약으로 재발급하면 같은 티켓을 반환하고 행은 1건만 남는다")
    void reissueReturnsSameTicketAndKeepsOneRow() {
        IssueTicketsRequest request = new IssueTicketsRequest(RESERVATION_ID, 10L, 45L, 77L, 3);

        IssuedTicketResponse first = ticketService.issue(request);
        IssuedTicketResponse second = ticketService.issue(request);

        assertThat(second.ticketId()).isEqualTo(first.ticketId());
        assertThat(second.checkinToken()).isEqualTo(first.checkinToken());
        assertThat(ticketRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("reservation_id UNIQUE 제약이 실제로 걸려 있어 같은 예약의 중복 행 저장을 막는다")
    void reservationIdIsUniqueInDatabase() {
        ticketRepository.saveAndFlush(
                Ticket.issue(RESERVATION_ID, 10L, 45L, 77L, 1, "token-a", NOW));

        assertThatThrownBy(() -> ticketRepository.saveAndFlush(
                Ticket.issue(RESERVATION_ID, 10L, 45L, 77L, 1, "token-b", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
