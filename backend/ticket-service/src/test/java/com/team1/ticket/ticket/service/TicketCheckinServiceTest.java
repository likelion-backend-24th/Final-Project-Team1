package com.team1.ticket.ticket.service;

import com.team1.ticket.client.ExpoClient;
import com.team1.ticket.client.ExpoSummary;
import com.team1.ticket.common.ApiException;
import com.team1.ticket.common.ErrorCode;
import com.team1.ticket.ticket.dto.CheckinResult;
import com.team1.ticket.ticket.dto.CheckinTicketView;
import com.team1.ticket.ticket.entity.Ticket;
import com.team1.ticket.ticket.entity.TicketStatus;
import com.team1.ticket.ticket.repository.TicketRepository;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketCheckinServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-20T05:00:00Z");
    private static final long EXPO_ID = 10L;
    private static final long OWNER_ID = 7L;
    private static final String TOKEN = "tok-1";

    private static final AuthenticatedUser OWNER = new AuthenticatedUser(OWNER_ID, "ORGANIZER");
    private static final AuthenticatedUser OTHER_ORGANIZER = new AuthenticatedUser(99L, "ORGANIZER");
    private static final AuthenticatedUser MEMBER = new AuthenticatedUser(OWNER_ID, "USER");

    private TicketRepository tickets;
    private ExpoClient expoClient;
    private TicketCheckinService service;

    @BeforeEach
    void setUp() {
        tickets = mock(TicketRepository.class);
        expoClient = mock(ExpoClient.class);
        service = new TicketCheckinService(tickets, expoClient, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Ticket issuedTicket() {
        return Ticket.issue(123L, EXPO_ID, 45L, 77L, 2, TOKEN, NOW.minusSeconds(3600));
    }

    private void ownedExpo() {
        when(expoClient.getExpo(EXPO_ID)).thenReturn(new ExpoSummary(EXPO_ID, OWNER_ID, "PUBLISHED"));
    }

    // ---- verify ----

    @Test
    @DisplayName("verify: 소유 주최자가 토큰으로 조회하면 티켓 정보를 반환하고 상태는 그대로다(미전이)")
    void verifyReturnsInfoWithoutTransition() {
        Ticket ticket = issuedTicket();
        when(tickets.findByCheckinToken(TOKEN)).thenReturn(Optional.of(ticket));
        ownedExpo();

        CheckinTicketView view = service.verify(TOKEN, OWNER);

        assertThat(view.status()).isEqualTo("ISSUED");
        assertThat(view.headcount()).isEqualTo(2);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ISSUED); // 미전이
    }

    @Test
    @DisplayName("verify: 존재하지 않는 토큰이면 404")
    void verifyRejectsUnknownToken() {
        when(tickets.findByCheckinToken(TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(TOKEN, OWNER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("verify: 주최자 역할이 아니면 403 이고 박람회 조회조차 하지 않는다")
    void verifyRejectsNonOrganizer() {
        when(tickets.findByCheckinToken(TOKEN)).thenReturn(Optional.of(issuedTicket()));

        assertThatThrownBy(() -> service.verify(TOKEN, MEMBER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(expoClient, never()).getExpo(anyLong());
    }

    @Test
    @DisplayName("verify: 다른 주최자면 403")
    void verifyRejectsNonOwner() {
        when(tickets.findByCheckinToken(TOKEN)).thenReturn(Optional.of(issuedTicket()));
        ownedExpo();

        assertThatThrownBy(() -> service.verify(TOKEN, OTHER_ORGANIZER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    // ---- checkin ----

    @Test
    @DisplayName("checkin: 소유 주최자가 확정하면 USED 로 전이하고 사용 시각을 기록한다")
    void checkinTransitionsToUsed() {
        Ticket ticket = issuedTicket();
        when(tickets.findById(anyLong())).thenReturn(Optional.of(ticket));
        ownedExpo();

        CheckinResult result = service.checkin(1L, OWNER);

        assertThat(result.status()).isEqualTo("USED");
        assertThat(result.checkedInAt()).isEqualTo(NOW);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.USED);
    }

    @Test
    @DisplayName("checkin: 없는 티켓이면 404")
    void checkinRejectsUnknownTicket() {
        when(tickets.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkin(1L, OWNER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("checkin: 이미 체크인된 티켓이면 409")
    void checkinRejectsAlreadyUsed() {
        Ticket ticket = issuedTicket();
        ticket.checkIn(NOW.minusSeconds(60));
        when(tickets.findById(anyLong())).thenReturn(Optional.of(ticket));
        ownedExpo();

        assertThatThrownBy(() -> service.checkin(1L, OWNER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    @DisplayName("checkin: 다른 주최자면 403 이고 전이하지 않는다")
    void checkinRejectsNonOwner() {
        Ticket ticket = issuedTicket();
        when(tickets.findById(anyLong())).thenReturn(Optional.of(ticket));
        ownedExpo();

        assertThatThrownBy(() -> service.checkin(1L, OTHER_ORGANIZER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ISSUED);
    }

    @Test
    @DisplayName("checkin: 박람회 조회 실패면 503(fail-closed)이고 전이하지 않는다")
    void checkinFailsClosedWhenExpoUnavailable() {
        Ticket ticket = issuedTicket();
        when(tickets.findById(anyLong())).thenReturn(Optional.of(ticket));
        when(expoClient.getExpo(EXPO_ID))
                .thenThrow(new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "expo-service unavailable"));

        assertThatThrownBy(() -> service.checkin(1L, OWNER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ISSUED);
    }
}
