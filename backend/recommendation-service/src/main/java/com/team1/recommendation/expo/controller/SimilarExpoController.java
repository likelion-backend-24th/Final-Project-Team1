package com.team1.recommendation.expo.controller;

import com.team1.recommendation.common.ApiResponse;
import com.team1.recommendation.expo.dto.SimilarExposResponse;
import com.team1.recommendation.expo.service.SimilarExpoService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/expos")
public class SimilarExpoController {

    private final SimilarExpoService similarExpoService;

    public SimilarExpoController(SimilarExpoService similarExpoService) {
        this.similarExpoService = similarExpoService;
    }

    @GetMapping("/{expoId}/similar")
    public ApiResponse<SimilarExposResponse> getSimilar(@PathVariable Long expoId) {
        return ApiResponse.ok(similarExpoService.findSimilar(expoId));
    }
}
