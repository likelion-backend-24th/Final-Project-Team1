package com.team1.expo.expo.controller;

import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.common.response.ApiResponse;
import com.team1.expo.expo.dto.CreateExpoRequest;
import com.team1.expo.expo.dto.ExpoResponse;
import com.team1.expo.expo.dto.UpdateExpoRequest;
import com.team1.expo.expo.service.ExpoService;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/channels/{channelId}/expos")
@RequiredArgsConstructor
public class ExpoController {

    private final ExpoService expoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExpoResponse> createExpo(
            @PathVariable Long channelId,
            @Valid @RequestBody CreateExpoRequest request) {
        AuthenticatedUser user = getOrganizer();
        return ApiResponse.ok(expoService.create(user.userId(), channelId, request));
    }

    // 주최자용 목록. GET /api/v1/expos 는 PUBLISHED 만 내려주므로 HIDDEN 이 여기서만 보인다.
    @GetMapping
    public ApiResponse<java.util.List<ExpoResponse>> listExpos(@PathVariable Long channelId) {
        AuthenticatedUser user = getOrganizer();
        return ApiResponse.ok(expoService.listForOrganizer(user.userId(), channelId));
    }

    // 주최자용 단건. 본인 채널이면 HIDDEN·CLOSED 도 200, 남의 것은 404.
    @GetMapping("/{expoId}")
    public ApiResponse<ExpoResponse> getExpo(@PathVariable Long channelId, @PathVariable Long expoId) {
        AuthenticatedUser user = getOrganizer();
        return ApiResponse.ok(expoService.getForOrganizer(user.userId(), channelId, expoId));
    }

    @PatchMapping("/{expoId}")
    public ApiResponse<ExpoResponse> updateExpo(
            @PathVariable Long channelId,
            @PathVariable Long expoId,
            @Valid @RequestBody UpdateExpoRequest request) {
        AuthenticatedUser user = getOrganizer();
        return ApiResponse.ok(expoService.update(user.userId(), channelId, expoId, request));
    }

    private AuthenticatedUser getOrganizer() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        if (!"ORGANIZER".equals(user.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }
}
