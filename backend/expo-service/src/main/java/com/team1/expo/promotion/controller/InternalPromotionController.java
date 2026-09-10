package com.team1.expo.promotion.controller;

import com.team1.expo.promotion.dto.InternalPromotionPaymentResponse;
import com.team1.expo.promotion.service.ExpoPromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/internal/expo-promotions")
@RequiredArgsConstructor
public class InternalPromotionController {

    private final ExpoPromotionService promotionService;

    /** 계약 2 — Settlement-Service가 호출. InternalTokenFilter가 인증한다. */
    @GetMapping("/payments")
    public List<InternalPromotionPaymentResponse> payments(
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return promotionService.getPaymentsForSettlement(from, to);
    }
}
