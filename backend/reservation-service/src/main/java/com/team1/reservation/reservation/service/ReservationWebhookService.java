package com.team1.reservation.reservation.service;

import com.team1.payment.PaymentApprovalOutcome;
import com.team1.payment.WebhookProcessResult;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.TraceId;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


//웹훅 처리 결과를 예약에 반영한다.


@Service
public class ReservationWebhookService {

    private static final Logger log = LoggerFactory.getLogger(ReservationWebhookService.class);

    private final ReservationRepository reservations;
    private final ReservationPaymentService reservationPaymentService;

    public ReservationWebhookService(ReservationRepository reservations,
                                     ReservationPaymentService reservationPaymentService) {
        this.reservations = reservations;
        this.reservationPaymentService = reservationPaymentService;
    }

    /** @return 재시도가 필요하면 false. Controller 가 이 값으로 200/503 을 가른다. */
    @Transactional
    public boolean apply(WebhookProcessResult processed) {
        PaymentApprovalOutcome outcome = processed.approvalResult().outcome();
        Long refId = processed.refId();

        // refId 가 없다는 것은 우리 결제가 아니거나 아직 우리 DB 에 없다는 뜻이다.
        // 재전송해도 달라지지 않으므로 기록만 남기고 받아들인다.
        if (refId == null) {
            log.info("webhook accepted without reservation outcome={} reason={} traceId={}",
                    outcome, processed.approvalResult().failureReason(), TraceId.get());
            return true;
        }

        if (outcome == PaymentApprovalOutcome.ALREADY_PROCESSED
                || outcome == PaymentApprovalOutcome.IGNORED) {
            log.info("webhook already handled reservationId={} outcome={} traceId={}",
                    refId, outcome, TraceId.get());
            return true;
        }

        // refId 는 있는데 결제 결과를 모르는 경우다. 재전송하면 풀릴 수 있으므로 503 을 준다.
        if (outcome == PaymentApprovalOutcome.UNKNOWN) {
            log.warn("webhook payment result unknown reservationId={} reason={} traceId={}",
                    refId, processed.approvalResult().failureReason(), TraceId.get());
            return false;
        }

        Reservation reservation = reservations.findById(refId).orElse(null);
        if (reservation == null) {
            log.warn("webhook target reservation not found reservationId={} traceId={}", refId, TraceId.get());
            return true;
        }

        try {
            reservationPaymentService.applyOutcome(reservation, processed.approvalResult());
        } catch (ApiException e) {
            // 이미 최종 상태이거나 금액 불일치다. 둘 다 재전송으로 해소되지 않는다.
            log.warn("webhook could not be applied reservationId={} code={} traceId={}",
                    refId, e.code(), TraceId.get());
        }
        return true;
    }
}
