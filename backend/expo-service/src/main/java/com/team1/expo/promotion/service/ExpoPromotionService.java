package com.team1.expo.promotion.service;

import com.team1.expo.client.PortOneClient;
import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.channel.ChannelRepository;
import com.team1.expo.domain.expo.ExpoRepository;
import com.team1.expo.domain.promotion.*;
import com.team1.expo.promotion.dto.ApplyPromotionRequest;
import com.team1.expo.promotion.dto.ApplyPromotionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpoPromotionService {

    static final int BANNER_PRICE = 9_900;

    private final ExpoPromotionRepository promotionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ExpoRepository expoRepository;
    private final ChannelRepository channelRepository;
    private final PortOneClient portOneClient;
    private final Clock clock;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public ApplyPromotionResponse apply(Long requesterId, ApplyPromotionRequest request) {
        verifyOwnership(request.expoId(), requesterId);

        if (promotionRepository.existsByExpoIdAndStatusIn(
                request.expoId(), List.of(ExpoPromotionStatus.PENDING, ExpoPromotionStatus.ACTIVE))) {
            throw new BusinessException(ErrorCode.PROMOTION_ALREADY_EXISTS);
        }

        ExpoPromotion promotion = promotionRepository.save(
                ExpoPromotion.create(request.expoId(), BANNER_PRICE, clock));

        String noticeUrl = baseUrl + "/api/v1/expo-promotions/webhooks/portone";
        String pgTransactionId = portOneClient.createPaymentOrder(BANNER_PRICE, noticeUrl);

        paymentTransactionRepository.save(
                PaymentTransaction.pending(promotion.getId(), BANNER_PRICE, pgTransactionId, clock));

        return new ApplyPromotionResponse(
                promotion.getId(),
                promotion.getExpoId(),
                promotion.getAmount(),
                pgTransactionId,
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

        portOneClient.refund(tx.getPgTransactionId(), tx.getAmount());

        tx.markCancelled(clock);
        promotion.cancel(clock);
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
