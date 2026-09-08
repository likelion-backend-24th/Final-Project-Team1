package com.team1.expo.promotion.service;

import com.team1.expo.client.PortOneClient;
import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.promotion.*;
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
    private final PortOneClient portOneClient;
    private final Clock clock;

    @Transactional
    public void handle(byte[] rawBody, String signature, PortOneWebhookPayload payload) {
        if (!portOneClient.verifyWebhookSignature(rawBody, signature)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        // 중복 처리 방지 (멱등)
        if (webhookEventRepository.existsByWebhookId(payload.webhookId())) {
            log.info("중복 웹훅 무시 webhookId={}", payload.webhookId());
            return;
        }

        PaymentTransaction tx = paymentTransactionRepository.findByPgTransactionId(payload.pgTransactionId())
                .orElseGet(() -> {
                    log.warn("웹훅 수신했으나 payment_transaction 없음 pgTransactionId={}", payload.pgTransactionId());
                    return null;
                });

        WebhookEvent event = webhookEventRepository.save(
                WebhookEvent.received(payload.webhookId(),
                        tx != null ? tx.getId() : null,
                        payload.eventType(), clock));

        if (tx == null) {
            log.warn("처리 불가 웹훅 건너뜀 webhookId={}", payload.webhookId());
            return;
        }

        ExpoPromotion promotion = promotionRepository.findById(tx.getRefId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        switch (payload.eventType()) {
            case "paid" -> {
                tx.markPaid(clock);
                promotion.confirm(clock);
            }
            case "cancelled", "failed" -> {
                tx.markCancelled(clock);
                promotion.cancel(clock);
            }
            default -> log.warn("미지원 이벤트 타입 eventType={} webhookId={}", payload.eventType(), payload.webhookId());
        }

        event.markProcessed(clock);
    }

    public record PortOneWebhookPayload(
            String webhookId,
            String pgTransactionId,
            String eventType
    ) {}
}
