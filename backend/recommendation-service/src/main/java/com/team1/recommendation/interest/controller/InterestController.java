package com.team1.recommendation.interest.controller;

import com.team1.recommendation.common.ApiException;
import com.team1.recommendation.common.ApiResponse;
import com.team1.recommendation.common.ErrorCode;
import com.team1.recommendation.interest.dto.InterestsResponse;
import com.team1.recommendation.interest.dto.UpsertInterestsRequest;
import com.team1.recommendation.interest.service.InterestService;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class InterestController {

    private final InterestService interestService;

    public InterestController(InterestService interestService) {
        this.interestService = interestService;
    }

    @PutMapping("/interests")
    public ApiResponse<InterestsResponse> upsertInterests(
            @Valid @RequestBody UpsertInterestsRequest request) {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) throw new ApiException(ErrorCode.UNAUTHENTICATED, "authentication required");
        return ApiResponse.ok(interestService.upsert(user.userId(), request));
    }
}
