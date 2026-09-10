package com.team1.expo.promotion.dto;

import com.team1.expo.domain.expo.Expo;
import com.team1.expo.domain.promotion.ExpoPromotion;

import java.time.LocalDateTime;

public record ActivePromotionResponse(
        Long promotionId,
        Long expoId,
        String title,
        String thumbnailUrl,
        String region,
        String category,
        LocalDateTime paidAt
) {
    public static ActivePromotionResponse of(ExpoPromotion p, Expo expo) {
        return new ActivePromotionResponse(
                p.getId(), p.getExpoId(),
                expo.getTitle(), expo.getThumbnailUrl(), expo.getRegion(), expo.getCategory(),
                p.getPaidAt());
    }
}
