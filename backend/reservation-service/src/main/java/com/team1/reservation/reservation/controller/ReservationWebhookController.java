package com.team1.reservation.reservation.controller;

import com.team1.payment.WebhookProcessResult;
import com.team1.payment.WebhookVerificationFailedException;
import com.team1.payment.WebhookService;
import com.team1.reservation.common.ApiResponse;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.common.TraceId;
import com.team1.reservation.reservation.service.ReservationWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


//PortOne 예약 결제 웹훅 수신.


@RestController
@RequestMapping("/api/v1/reservations/webhooks/portone")
public class ReservationWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ReservationWebhookController.class);

    private final WebhookService webhookService;
    private final ReservationWebhookService reservationWebhookService;

    public ReservationWebhookController(WebhookService webhookService,
                                        ReservationWebhookService reservationWebhookService) {
        this.webhookService = webhookService;
        this.reservationWebhookService = reservationWebhookService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> receive(
            @RequestBody String body,
            @RequestHeader("webhook-id") String webhookId,
            @RequestHeader("webhook-signature") String webhookSignature,
            @RequestHeader("webhook-timestamp") String webhookTimestamp) {

        WebhookProcessResult processed;
        try {
            processed = webhookService.process(body, webhookId, webhookSignature, webhookTimestamp);

        } catch (WebhookVerificationFailedException e) {
            // 서명이 맞지 않으면 우리가 보낸 적 없는 요청이다. 상태를 바꾸지 않는다.
            log.warn("webhook signature verification failed webhookId={} traceId={}", webhookId, TraceId.get());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Bearer")
                    .body(ApiResponse.fail(ErrorCode.UNAUTHENTICATED, TraceId.get(), "invalid webhook signature"));
        }

        boolean settled = reservationWebhookService.apply(processed);

        return settled
                ? ResponseEntity.ok(ApiResponse.ok(null))
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.fail(ErrorCode.DEPENDENCY_UNAVAILABLE, TraceId.get(),
                                "payment result unknown, retry expected"));
    }
}
