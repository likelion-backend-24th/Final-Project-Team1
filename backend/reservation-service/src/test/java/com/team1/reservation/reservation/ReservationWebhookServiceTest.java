package com.team1.reservation.reservation;

import com.team1.payment.PaymentApprovalResult;
import com.team1.payment.WebhookProcessResult;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.reservation.service.ReservationPaymentService;
import com.team1.reservation.reservation.service.ReservationWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 웹훅 응답 코드가 재시도를 좌우한다. 재전송해도 결과가 달라지지 않는 경우까지 503 을 주면
 * PortOne 이 같은 웹훅을 무한히 되돌려보낸다.
 */
class ReservationWebhookServiceTest {

    private static final Long RESERVATION_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-09-09T04:00:00Z");

    private ReservationRepository reservations;
    private ReservationPaymentService reservationPaymentService;
    private ReservationWebhookService service;

    @BeforeEach
    void setUp() {
        reservations = mock(ReservationRepository.class);
        reservationPaymentService = mock(ReservationPaymentService.class);
        service = new ReservationWebhookService(reservations, reservationPaymentService);
    }

    private Reservation pending() {
        return Reservation.create("R-4K7Q-W2M8", 7L, 1L, 100L,
                "홍길동", "01012345678", 2, 20000, NOW);
    }

    private WebhookProcessResult processed(Long refId, PaymentApprovalResult result) {
        return new WebhookProcessResult(refId, result);
    }

    @Test
    @DisplayName("결제 성공이면 예약에 반영하고 200 으로 받아들인다")
    void appliesSuccess() {
        Reservation reservation = pending();
        when(reservations.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

        boolean settled = service.apply(processed(RESERVATION_ID, PaymentApprovalResult.success(20000)));

        assertThat(settled).isTrue();
        verify(reservationPaymentService).applyOutcome(any(), any());
    }

    @Test
    @DisplayName("우리 결제가 아니면 refId 가 없다 - 재전송해도 같으므로 200")
    void acceptsWebhookWithoutRefId() {
        boolean settled = service.apply(processed(null, PaymentApprovalResult.ignored("결제 관련 웹훅 아님")));

        assertThat(settled).isTrue();
        verifyNoInteractions(reservations, reservationPaymentService);
    }

    @Test
    @DisplayName("알 수 없는 paymentId 도 refId 가 없다 - 재전송해도 영원히 모르므로 200")
    void acceptsUnknownPaymentId() {
        boolean settled = service.apply(processed(null, PaymentApprovalResult.unknown("알 수 없는 paymentId")));

        assertThat(settled).isTrue();
        verifyNoInteractions(reservationPaymentService);
    }

    @Test
    @DisplayName("이미 처리한 웹훅은 200 - 여기서 503 을 주면 무한 재시도가 된다")
    void acceptsAlreadyProcessed() {
        boolean settled = service.apply(processed(RESERVATION_ID, PaymentApprovalResult.alreadyProcessed()));

        assertThat(settled).isTrue();
        verifyNoInteractions(reservationPaymentService);
    }

    @Test
    @DisplayName("예약은 아는데 결제 결과를 모르면 503 - 재전송하면 풀릴 수 있다")
    void asksRetryWhenUnknownWithRefId() {
        boolean settled = service.apply(processed(RESERVATION_ID, PaymentApprovalResult.unknown("PG 무응답")));

        assertThat(settled).isFalse();
        verify(reservations, never()).findById(anyLong());
    }

    @Test
    @DisplayName("이미 최종 상태인 예약이면 반영에 실패해도 200 - 재전송으로 해소되지 않는다")
    void acceptsWhenReservationAlreadyTerminal() {
        when(reservations.findById(RESERVATION_ID)).thenReturn(Optional.of(pending()));
        doThrow(new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "already CANCELLED"))
                .when(reservationPaymentService).applyOutcome(any(), any());

        assertThat(service.apply(processed(RESERVATION_ID, PaymentApprovalResult.success(20000)))).isTrue();
    }

    @Test
    @DisplayName("금액 불일치도 200 - 사람이 확인할 문제라 재전송이 의미 없다")
    void acceptsAmountMismatch() {
        when(reservations.findById(RESERVATION_ID)).thenReturn(Optional.of(pending()));
        doThrow(new ApiException(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "mismatch"))
                .when(reservationPaymentService).applyOutcome(any(), any());

        assertThat(service.apply(processed(RESERVATION_ID, PaymentApprovalResult.amountMismatch()))).isTrue();
    }

    @Test
    @DisplayName("refId 가 가리키는 예약이 없으면 200 - 우리 데이터에 없는 건 재전송해도 같다")
    void acceptsMissingReservation() {
        when(reservations.findById(RESERVATION_ID)).thenReturn(Optional.empty());

        assertThat(service.apply(processed(RESERVATION_ID, PaymentApprovalResult.success(20000)))).isTrue();
        verifyNoInteractions(reservationPaymentService);
    }
}
