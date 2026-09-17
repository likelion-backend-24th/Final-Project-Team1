package com.team1.recommendation.preference.controller;

import com.team1.recommendation.common.ApiException;
import com.team1.recommendation.common.ApiResponse;
import com.team1.recommendation.common.ErrorCode;
import com.team1.recommendation.preference.dto.PreferencesResponse;
import com.team1.recommendation.preference.dto.UpsertPreferencesRequest;
import com.team1.recommendation.preference.service.PreferenceService;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class PreferenceController {

    private final PreferenceService preferenceService;

    public PreferenceController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping("/interests")
    public ApiResponse<PreferencesResponse> getInterests() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) throw new ApiException(ErrorCode.UNAUTHENTICATED, "authentication required");
        return ApiResponse.ok(preferenceService.get(user.userId()));
    }

    @PutMapping("/interests")
    public ApiResponse<PreferencesResponse> upsertInterests(
            @Valid @RequestBody UpsertPreferencesRequest request) {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) throw new ApiException(ErrorCode.UNAUTHENTICATED, "authentication required");
        return ApiResponse.ok(preferenceService.upsert(user.userId(), request));
    }
}
