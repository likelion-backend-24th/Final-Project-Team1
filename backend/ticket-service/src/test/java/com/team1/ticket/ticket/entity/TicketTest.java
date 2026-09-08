package com.team1.ticket.ticket.entity;

import com.team1.ticket.common.ApiException;
import com.team1.ticket.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketTest {

    private static final Instant NOW = Instant.parse("2026-09-07T02:00:00Z");

    @Test
    @DisplayName("발급하면 ISSUED 상태로 필드가 채워지고 사용시각은 비어 있다")
    void issuesTicket() {
        Ticket ticket = Ticket.issue(123L, 10L, 45L, 77L, "tok-1", NOW);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ISSUED);
        assertThat(ticket.getReservationId()).isEqualTo(123L);
        assertThat(ticket.getExpoId()).isEqualTo(10L);
        assertThat(ticket.getRoundId()).isEqualTo(45L);
        assertThat(ticket.getUserId()).isEqualTo(77L);
        assertThat(ticket.getCheckinToken()).isEqualTo("tok-1");
        assertThat(ticket.getIssuedAt()).isEqualTo(NOW);
        assertThat(ticket.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("필수 식별자가 null 이면 발급을 거절한다")
    void rejectsNullIdentifiers() {
        assertThatThrownBy(() -> Ticket.issue(null, 10L, 45L, 77L, "tok", NOW))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("무효화하면 CANCELLED 로 전이한다")
    void cancelsIssued() {
        Ticket ticket = Ticket.issue(123L, 10L, 45L, 77L, "tok", NOW);

        ticket.cancel();

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }
}
