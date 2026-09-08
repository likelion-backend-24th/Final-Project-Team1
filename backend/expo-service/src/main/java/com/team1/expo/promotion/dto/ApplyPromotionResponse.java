package com.team1.expo.promotion.dto;

public record ApplyPromotionResponse(
        Long promotionId,
        Long expoId,
        int amount,
        String pgTransactionId,
        String status
) {}
