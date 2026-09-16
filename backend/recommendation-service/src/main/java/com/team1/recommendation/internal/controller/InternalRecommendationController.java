package com.team1.recommendation.internal.controller;

import com.team1.recommendation.common.ApiResponse;
import com.team1.recommendation.expo.service.ExpoTagService;
import com.team1.recommendation.internal.dto.ExpoPublishedRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/recommendations")
public class InternalRecommendationController {

    private final ExpoTagService expoTagService;

    public InternalRecommendationController(ExpoTagService expoTagService) {
        this.expoTagService = expoTagService;
    }

    @PostMapping("/expo-published")
    public ApiResponse<Void> expoPublished(@RequestBody ExpoPublishedRequest request) {
        expoTagService.handleExpoPublished(request);
        return ApiResponse.ok(null);
    }
}
