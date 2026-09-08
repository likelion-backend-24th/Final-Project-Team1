package com.team1.expo.promotion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ApplyPromotionRequest(
        @NotNull @Positive Long expoId
) {}
