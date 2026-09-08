package com.team1.expo.promotion.dto;

import com.team1.expo.domain.promotion.ExpoPromotion;

import java.time.LocalDateTime;

public record ActivePromotionResponse(
        Long promotionId,
        Long expoId,
        LocalDateTime paidAt
) {
    public static ActivePromotionResponse from(ExpoPromotion p) {
        return new ActivePromotionResponse(p.getId(), p.getExpoId(), p.getPaidAt());
    }
}
