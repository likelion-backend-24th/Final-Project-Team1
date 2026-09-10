package com.team1.reservation.reservation;

import com.team1.payment.PaymentStatus;
import com.team1.reservation.client.TicketDetail;
import com.team1.reservation.reservation.dto.MyReservationDetailResponse;
import com.team1.reservation.reservation.dto.MyReservationResponse;
import com.team1.reservation.reservation.entity.RefundState;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.support.MyReservationFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** #82 내 예약 조회. */
class MyReservationQueryTest extends MyReservationFixture {

    private static final TicketDetail TICKET =
            new TicketDetail(900L, "chk-abc", NOW, "ISSUED");

    @BeforeEach
    void setUp() {
        initMocks();
    }

    @Test
    @DisplayName("상세에 회차 시각과 본인 연락처가 담긴다 - 본인 예약이라 마스킹하지 않는다")
    void detailCarriesScheduleAndContact() {
        givenMine(ReservationStatus.CONFIRMED);
        when(ticketClient.findTicket(RESERVATION_ID)).thenReturn(TICKET);

        MyReservationDetailResponse detail = service.getMine(RESERVATION_ID, MEMBER);

        assertThat(detail.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(detail.startsAt()).isEqualTo(round().getStartsAt());
        assertThat(detail.endsAt()).isEqualTo(round().getEndsAt());
        assertThat(detail.contactName()).isEqualTo("홍길동");
        assertThat(detail.contactPhone()).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("상세에 QR 이 담기고 ticketAvailable 이 true 다")
    void detailCarriesTicket() {
        givenMine(ReservationStatus.CONFIRMED);
        when(ticketClient.findTicket(RESERVATION_ID)).thenReturn(TICKET);

        MyReservationDetailResponse detail = service.getMine(RESERVATION_ID, MEMBER);

        assertThat(detail.ticketAvailable()).isTrue();
        assertThat(detail.ticket().checkinToken()).isEqualTo("chk-abc");
        assertThat(detail.ticket().status()).isEqualTo("ISSUED");
    }

    @Test
    @DisplayName("티켓을 못 가져와도 예약 정보는 그대로 내려간다 - 부분 실패 허용")
    void detailSurvivesTicketLookupFailure() {
        givenMine(ReservationStatus.CONFIRMED);
        // 아직 발급 안 됐거나(404) 조회에 실패한 경우 모두 null 이다.
        when(ticketClient.findTicket(RESERVATION_ID)).thenReturn(null);

        MyReservationDetailResponse detail = service.getMine(RESERVATION_ID, MEMBER);

        assertThat(detail.ticketAvailable()).isFalse();
        assertThat(detail.ticket()).isNull();
        assertThat(detail.reservationNo()).isNotNull();
        assertThat(detail.status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("환불 상태를 파생해 내려준다 - 화면이 조합표를 몰라도 된다")
    void derivesRefundState() {
        givenMine(ReservationStatus.CANCELLED);
        givenPayment(RESERVATION_ID, PaymentStatus.REFUND_FAILED, MAX_REFUND_ATTEMPTS);

        assertThat(service.getMine(RESERVATION_ID, MEMBER).refundState())
                .isEqualTo(RefundState.REFUND_UNRESOLVED);
        assertThat(service.listMine(MEMBER).get(0).refundState())
                .isEqualTo(RefundState.REFUND_UNRESOLVED);
    }

    @Test
    @DisplayName("목록에는 연락처와 QR 이 실리지 않는다")
    void listOmitsContactAndTicket() {
        givenMine(ReservationStatus.CONFIRMED);

        List<MyReservationResponse> mine = service.listMine(MEMBER);

        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).startsAt()).isEqualTo(round().getStartsAt());
        // 목록에서는 Ticket-Service 를 부르지 않는다. N건이면 N번 호출이 된다.
        verify(ticketClient, times(0)).findTicket(anyLong());
    }

    @Test
    @DisplayName("목록은 회차·결제를 한 번씩만 조회한다 - 예약마다 부르면 N+1 이다")
    void listBatchesLookups() {
        givenMine(ReservationStatus.CONFIRMED);

        service.listMine(MEMBER);

        verify(rounds, times(1)).findAllById(any());
        verify(payments, times(1)).findByRefIdIn(any());
        verify(rounds, times(0)).findById(anyLong());
    }

    @Test
    @DisplayName("상세 응답 toString 에 연락처가 남지 않는다")
    void detailToStringHidesContact() {
        givenMine(ReservationStatus.CONFIRMED);
        when(ticketClient.findTicket(RESERVATION_ID)).thenReturn(TICKET);

        String printed = service.getMine(RESERVATION_ID, MEMBER).toString();

        assertThat(printed).doesNotContain("홍길동").doesNotContain("01012345678");
        assertThat(printed).contains("reservationId=" + RESERVATION_ID);
    }
}
