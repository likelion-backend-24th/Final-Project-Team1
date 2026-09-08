package com.team1.expo.promotion.service;

import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.channel.ChannelRepository;
import com.team1.expo.domain.expo.ExpoRepository;
import com.team1.expo.domain.promotion.*;
import com.team1.expo.promotion.dto.ActivePromotionResponse;
import com.team1.expo.promotion.dto.ApplyPromotionRequest;
import com.team1.expo.promotion.dto.ApplyPromotionResponse;
import com.team1.payment.PaymentIdGenerator;
import com.team1.payment.PaymentTransaction;
import com.team1.payment.PgCancelResult;
import com.team1.payment.PgClient;
import com.team1.payment.PgCommunicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpoPromotionService {

    static final int BANNER_PRICE = 9_900;

    private final ExpoPromotionRepository promotionRepository;
    private final ExpoPaymentTransactionRepository paymentTransactionRepository;
    private final ExpoRepository expoRepository;
    private final ChannelRepository channelRepository;
    private final PgClient pgClient;
    private final PaymentIdGenerator paymentIdGenerator;
    private final Clock clock;

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
                tx.markRefundFailed("PG 환불 거절 code=" + result.responseCode(), clock.instant());
            }
        } catch (PgCommunicationException e) {
            tx.markRefundFailed("PG 통신 실패: " + e.getMessage(), clock.instant());
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    @Transactional(readOnly = true)
    public List<ActivePromotionResponse> getActive() {
        return promotionRepository.findByStatusOrderByPaidAtAsc(ExpoPromotionStatus.ACTIVE)
                .stream().map(ActivePromotionResponse::from).toList();
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
