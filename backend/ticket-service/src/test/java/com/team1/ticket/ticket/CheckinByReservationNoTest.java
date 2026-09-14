package com.team1.ticket.ticket;

import com.team1.ticket.client.ExpoSummary;
import com.team1.ticket.common.ApiException;
import com.team1.ticket.common.ErrorCode;
import com.team1.ticket.support.IntegrationTestSupport;
import com.team1.ticket.ticket.dto.CheckinTicketView;
import com.team1.ticket.ticket.entity.Ticket;
import com.team1.ticket.ticket.entity.TicketStatus;
import com.team1.ticket.ticket.repository.TicketRepository;
import com.team1.ticket.ticket.service.TicketCheckinService;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// S7-2. 예약번호 조회는 파생 쿼리라 실제 DB 로 검증한다.
// 핵심은 마지막 테스트다 - 두 조회 경로가 같은 확정 경로를 쓰므로 중복 체크인이 경로와 무관하게 막힌다.
class CheckinByReservationNoTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-20T05:00:00Z");
    private static final long EXPO_ID = 10L;
    private static final long OWNER_ID = 7L;
    private static final String TOKEN = "tok-checkin-1";
    private static final String RESERVATION_NO = "R-4K7Q-W2M8";

    private static final AuthenticatedUser OWNER = new AuthenticatedUser(OWNER_ID, "ORGANIZER");
    private static final AuthenticatedUser OTHER_ORGANIZER = new AuthenticatedUser(99L, "ORGANIZER");

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketCheckinService checkinService;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        when(expoClient.getExpo(EXPO_ID)).thenReturn(new ExpoSummary(EXPO_ID, OWNER_ID, "PUBLISHED"));
    }

    private Ticket issued() {
        return ticketRepository.saveAndFlush(
                Ticket.issue(123L, RESERVATION_NO, EXPO_ID, 45L, 77L, 2, TOKEN, NOW.minusSeconds(3600)));
    }

    @Test
    @DisplayName("예약번호와 체크인 토큰은 같은 티켓을 가리킨다")
    void bothLookupsResolveToSameTicket() {
        Ticket ticket = issued();

        CheckinTicketView byNo = checkinService.verify(null, RESERVATION_NO, OWNER);
        CheckinTicketView byToken = checkinService.verify(TOKEN, null, OWNER);

        assertThat(byNo.ticketId()).isEqualTo(ticket.getId());
        assertThat(byNo.ticketId()).isEqualTo(byToken.ticketId());
    }

    @Test
    @DisplayName("없는 예약번호는 404")
    void rejectsUnknownReservationNo() {
        issued();

        assertThatThrownBy(() -> checkinService.verify(null, "R-0000-0000", OWNER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("남의 박람회 티켓은 예약번호로도 403")
    void rejectsOtherOrganizer() {
        issued();

        assertThatThrownBy(() -> checkinService.verify(null, RESERVATION_NO, OTHER_ORGANIZER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("QR 로 체크인한 뒤 예약번호로 재시도하면 409 - 중복 방지가 경로와 무관하다")
    void duplicateCheckinIsBlockedAcrossLookupPaths() {
        Ticket ticket = issued();
        checkinService.checkin(ticket.getId(), OWNER);

        Long sameTicketId = checkinService.verify(null, RESERVATION_NO, OWNER).ticketId();

        assertThatThrownBy(() -> checkinService.checkin(sameTicketId, OWNER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.USED);
    }
}
