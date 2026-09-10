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
    @DisplayName("발급하면 ISSUED 상태로 필드가 채워지고 headcount(입장 인원)를 담는다")
    void issuesTicket() {
        Ticket ticket = Ticket.issue(123L, 10L, 45L, 77L, 3, "tok-1", NOW);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ISSUED);
        assertThat(ticket.getReservationId()).isEqualTo(123L);
        assertThat(ticket.getExpoId()).isEqualTo(10L);
        assertThat(ticket.getRoundId()).isEqualTo(45L);
        assertThat(ticket.getUserId()).isEqualTo(77L);
        assertThat(ticket.getHeadcount()).isEqualTo(3);
        assertThat(ticket.getCheckinToken()).isEqualTo("tok-1");
        assertThat(ticket.getIssuedAt()).isEqualTo(NOW);
        assertThat(ticket.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("필수 식별자가 null 이면 발급을 거절한다")
    void rejectsNullIdentifiers() {
        assertThatThrownBy(() -> Ticket.issue(null, 10L, 45L, 77L, 1, "tok", NOW))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("headcount 가 1 미만이면 발급을 거절한다")
    void rejectsHeadcountBelowOne() {
        assertThatThrownBy(() -> Ticket.issue(123L, 10L, 45L, 77L, 0, "tok", NOW))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("무효화하면 CANCELLED 로 전이한다")
    void cancelsIssued() {
        Ticket ticket = Ticket.issue(123L, 10L, 45L, 77L, 1, "tok", NOW);

        ticket.cancel();

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }

    @Test
    @DisplayName("체크인하면 USED 로 전이하고 사용 시각을 기록한다")
    void checksIn() {
        Ticket ticket = Ticket.issue(123L, 10L, 45L, 77L, 2, "tok", NOW);
        Instant checkinAt = NOW.plusSeconds(3600);

        ticket.checkIn(checkinAt);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.USED);
        assertThat(ticket.getUsedAt()).isEqualTo(checkinAt);
    }

    @Test
    @DisplayName("이미 체크인된 티켓은 재체크인을 거부한다 (409)")
    void rejectsAlreadyCheckedIn() {
        Ticket ticket = Ticket.issue(123L, 10L, 45L, 77L, 1, "tok", NOW);
        ticket.checkIn(NOW);

        assertThatThrownBy(() -> ticket.checkIn(NOW.plusSeconds(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    @DisplayName("취소된 티켓은 체크인할 수 없다 (409)")
    void rejectsCheckInWhenCancelled() {
        Ticket ticket = Ticket.issue(123L, 10L, 45L, 77L, 1, "tok", NOW);
        ticket.cancel();

        assertThatThrownBy(() -> ticket.checkIn(NOW))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));
    }
}
