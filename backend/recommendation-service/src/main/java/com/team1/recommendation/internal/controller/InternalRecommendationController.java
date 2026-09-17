package com.team1.recommendation.internal.controller;

import com.team1.recommendation.activity.service.UserActivityService;
import com.team1.recommendation.common.ApiResponse;
import com.team1.recommendation.expo.service.ExpoTagService;
import com.team1.recommendation.internal.dto.BehaviorEventRequest;
import com.team1.recommendation.internal.dto.ExpoPublishedRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/recommendations")
public class InternalRecommendationController {

    private final ExpoTagService expoTagService;
    private final UserActivityService userActivityService;

    public InternalRecommendationController(ExpoTagService expoTagService,
                                            UserActivityService userActivityService) {
        this.expoTagService = expoTagService;
        this.userActivityService = userActivityService;
    }

    @PostMapping("/expo-published")
    public ApiResponse<Void> expoPublished(@RequestBody ExpoPublishedRequest request) {
        expoTagService.handleExpoPublished(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/events")
    public ApiResponse<Void> behaviorEvent(@RequestBody BehaviorEventRequest request) {
        userActivityService.record(request);
        return ApiResponse.ok(null);
    }
}
