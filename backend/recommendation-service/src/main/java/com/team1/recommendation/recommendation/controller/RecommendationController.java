package com.team1.recommendation.recommendation.controller;

import com.team1.recommendation.activity.entity.EventType;
import com.team1.recommendation.activity.service.UserActivityService;
import com.team1.recommendation.common.ApiException;
import com.team1.recommendation.common.ApiResponse;
import com.team1.recommendation.common.ErrorCode;
import com.team1.recommendation.internal.dto.BehaviorEventRequest;
import com.team1.recommendation.recommendation.dto.RecommendationResponse;
import com.team1.recommendation.recommendation.service.RecommendationService;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class RecommendationController {

    private final RecommendationService service;
    private final UserActivityService userActivityService;

    public RecommendationController(RecommendationService service,
                                    UserActivityService userActivityService) {
        this.service = service;
        this.userActivityService = userActivityService;
    }

    @GetMapping("/recommendations")
    public ApiResponse<RecommendationResponse> getRecommendations(
            @RequestParam(defaultValue = "10") int size) {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) throw new ApiException(ErrorCode.UNAUTHENTICATED, "authentication required");
        RecommendationResponse result = service.recommend(user.userId(), Math.min(size, 50));
        return ApiResponse.ok(result);
    }

    @PostMapping("/views/{expoId}")
    public ApiResponse<Void> recordView(@PathVariable Long expoId) {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) return ApiResponse.ok(null);
        userActivityService.record(new BehaviorEventRequest(user.userId(), expoId, EventType.PAGE_VIEWED, null, null));
        return ApiResponse.ok(null);
    }
}
