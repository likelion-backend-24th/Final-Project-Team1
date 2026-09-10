package com.team1.expo.promotion.service;

import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.channel.ChannelRepository;
import com.team1.expo.domain.expo.ExpoRepository;
import com.team1.expo.domain.promotion.*;
import com.team1.expo.promotion.dto.ActivePromotionResponse;
import com.team1.expo.promotion.dto.ApplyPromotionRequest;
import com.team1.expo.promotion.dto.ApplyPromotionResponse;
import com.team1.expo.promotion.dto.InternalPromotionPaymentResponse;
import com.team1.payment.PaymentIdGenerator;
import com.team1.payment.PaymentStatus;
import com.team1.payment.PaymentTransaction;
import com.team1.payment.PgCancelResult;
import com.team1.payment.PgClient;
import com.team1.payment.PgCommunicationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExpoPromotionService {

    static final int BANNER_PRICE = 9_900;

    private final ExpoPromotionRepository promotionRepository;
    private final ExpoPaymentTransactionRepository paymentTransactionRepository;
    private final ExpoRepository expoRepository;
    private final ChannelRepository channelRepository;
    private final PgClient pgClient;
    private final PaymentIdGenerator paymentIdGenerator;
    private final Clock clock;
    private final int refundMaxAttempts;
    private final Duration refundBackoff;

    public ExpoPromotionService(
            ExpoPromotionRepository promotionRepository,
            ExpoPaymentTransactionRepository paymentTransactionRepository,
            ExpoRepository expoRepository,
            ChannelRepository channelRepository,
            PgClient pgClient,
            PaymentIdGenerator paymentIdGenerator,
            Clock clock,
            @Value("${scheduler.refund-retry.max-attempts}") int refundMaxAttempts,
            @Value("${scheduler.refund-retry.backoff}") Duration refundBackoff
    ){
        this.promotionRepository = promotionRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.expoRepository = expoRepository;
        this.channelRepository = channelRepository;
        this.pgClient = pgClient;
        this.paymentIdGenerator = paymentIdGenerator;
        this.clock = clock;
        this.refundMaxAttempts = refundMaxAttempts;
        this.refundBackoff = refundBackoff;
    }

    @Transactional
    public ApplyPromotionResponse apply(Long requesterId, ApplyPromotionRequest request) {
        verifyOwnership(request.expoId(), requesterId);

        if (promotionRepository.existsByExpoIdAndStatusIn(
                request.expoId(), List.of(ExpoPromotionStatus.PENDING, ExpoPromotionStatus.ACTIVE))) {
            throw new BusinessException(ErrorCode.PROMOTION_ALREADY_EXISTS);
        }

        ExpoPromotion promotion = promotionRepository.save(
                ExpoPromotion.create(request.expoId(), BANNER_PRICE, clock));

        String paymentId = paymentIdGenerator.generate();
        paymentTransactionRepository.save(
                PaymentTransaction.create(promotion.getId(), paymentId, BANNER_PRICE, clock.instant()));

        return new ApplyPromotionResponse(
                promotion.getId(),
                promotion.getExpoId(),
                promotion.getAmount(),
                paymentId,
                promotion.getStatus().name()
        );
    }

    @Transactional
    public void refund(Long requesterId, Long promotionId) {
        ExpoPromotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        verifyOwnership(promotion.getExpoId(), requesterId);

        if (promotion.getStatus() != ExpoPromotionStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        PaymentTransaction tx = paymentTransactionRepository.findByRefId(promotionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        try {
            PgCancelResult result = pgClient.cancel(tx.getPaymentId(), tx.getAmount(), "배너 환불");
            if (result.success()) {
                tx.markCancelled(clock.instant());
                promotion.cancel(clock);
            } else {
                tx.markRefundFailed("PG 환불 거절 code=" + result.responseCode(), refundMaxAttempts, refundBackoff, clock.instant());
            }
        } catch (PgCommunicationException e) {
            tx.markRefundFailed("PG 통신 실패: " + e.getMessage(), refundMaxAttempts, refundBackoff, clock.instant());
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    @Transactional(readOnly = true)
    public List<ActivePromotionResponse> getActive() {
        List<ExpoPromotion> promotions = promotionRepository.findByStatusOrderByPaidAtAsc(ExpoPromotionStatus.ACTIVE);
        if (promotions.isEmpty()) return List.of();

        // ponytail: 30초 단위 순환 — 상태 없이 시계로만 회전. 수십 개 초과 시 DB 기반 커서로 교체
        int size = promotions.size();
        int offset = (int) ((clock.instant().getEpochSecond() / 30) % size);
        List<ExpoPromotion> rotated = new ArrayList<>(promotions.subList(offset, size));
        rotated.addAll(promotions.subList(0, offset));

        Map<Long, com.team1.expo.domain.expo.Expo> expoMap = expoRepository
                .findAllById(rotated.stream().map(ExpoPromotion::getExpoId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(com.team1.expo.domain.expo.Expo::getId, e -> e));

        return rotated.stream()
                .map(p -> ActivePromotionResponse.of(p, expoMap.get(p.getExpoId())))
                .toList();
    }

    private static final List<PaymentStatus> SETTLEMENT_STATUSES =
            List.of(PaymentStatus.PAID, PaymentStatus.CANCELLED);

    /** 계약 2 — Settlement-Service가 정산 집계에 사용. PAID·CANCELLED만 반환한다. */
    @Transactional(readOnly = true)
    public List<InternalPromotionPaymentResponse> getPaymentsForSettlement(Instant from, Instant to) {
        List<PaymentTransaction> txs =
                paymentTransactionRepository.findByStatusInAndUpdatedAtBetween(SETTLEMENT_STATUSES, from, to);

        Set<Long> promotionIds = txs.stream().map(PaymentTransaction::getRefId).collect(Collectors.toSet());
        Map<Long, Long> promotionToExpoId = promotionRepository.findAllById(promotionIds).stream()
                .collect(Collectors.toMap(ExpoPromotion::getId, ExpoPromotion::getExpoId));

        return txs.stream()
                .map(tx -> InternalPromotionPaymentResponse.of(tx, promotionToExpoId.get(tx.getRefId())))
                .toList();
    }

    private void verifyOwnership(Long expoId, Long requesterId) {
        Long ownerId = expoRepository.findById(expoId)
                .flatMap(expo -> channelRepository.findById(expo.getChannelId()))
                .map(ch -> ch.getOwnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!ownerId.equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
