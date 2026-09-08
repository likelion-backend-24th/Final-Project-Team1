package com.team1.expo.promotion.service;

import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.promotion.*;
import com.team1.payment.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class ExpoPromotionWebhookService {

    private static final Logger log = LoggerFactory.getLogger(ExpoPromotionWebhookService.class);

    private final ExpoPromotionRepository promotionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final PgClient pgClient;
    private final Clock clock;

    @Transactional
    public void handle(String webhookId, String paymentId, String eventType) {
        // 중복 처리 방지 (멱등)
        if (webhookEventRepository.existsByWebhookId(webhookId)) {
            log.info("중복 웹훅 무시 webhookId={}", webhookId);
            return;
        }

        WebhookEvent event = webhookEventRepository.save(
                WebhookEvent.receive(webhookId, paymentId, eventType));

        PaymentTransaction tx = paymentTransactionRepository.findByPaymentId(paymentId)
                .orElse(null);

        if (tx == null) {
            log.warn("payment_transaction 없음 paymentId={} webhookId={}", paymentId, webhookId);
            event.markIgnored();
            return;
        }

        try {
            PgInquiryResult inquiry = pgClient.inquire(paymentId);
            ExpoPromotion promotion = promotionRepository.findById(tx.getRefId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

            switch (inquiry.status()) {
                case PAID -> {
                    tx.markPaid(inquiry.pgTransactionId(), inquiry.responseCode());
                    promotion.confirm(clock);
                    event.markProcessed();
                }
                case FAILED -> {
                    tx.markFailed(inquiry.responseCode(), inquiry.failureReason());
                    promotion.cancel(clock);
                    event.markProcessed();
                }
                case NOT_FOUND -> {
                    log.warn("PG에서 결제 없음 paymentId={} webhookId={}", paymentId, webhookId);
                    event.markIgnored();
                }
            }
        } catch (PgCommunicationException e) {
            log.warn("PG 조회 실패 paymentId={} webhookId={}", paymentId, webhookId, e);
            event.markIgnored();
        }
    }

    public record PortOneWebhookPayload(
            String webhookId,
            String paymentId,
            String eventType
    ) {}
}
