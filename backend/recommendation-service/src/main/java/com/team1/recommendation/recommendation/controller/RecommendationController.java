package com.team1.recommendation.recommendation.controller;

import com.team1.recommendation.common.ApiException;
import com.team1.recommendation.common.ApiResponse;
import com.team1.recommendation.common.ErrorCode;
import com.team1.recommendation.recommendation.dto.RecommendationResponse;
import com.team1.recommendation.recommendation.service.RecommendationService;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @GetMapping("/recommendations")
    public ApiResponse<RecommendationResponse> getRecommendations(
            @RequestParam(defaultValue = "10") int size) {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) throw new ApiException(ErrorCode.UNAUTHENTICATED, "authentication required");
        RecommendationResponse result = service.recommend(user.userId(), Math.min(size, 50));
        return ApiResponse.ok(result);
    }
}
