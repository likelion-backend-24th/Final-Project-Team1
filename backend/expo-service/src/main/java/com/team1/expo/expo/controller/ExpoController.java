package com.team1.expo.expo.controller;

import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.common.response.ApiResponse;
import com.team1.expo.expo.dto.CreateExpoRequest;
import com.team1.expo.expo.dto.ExpoResponse;
import com.team1.expo.expo.service.ExpoService;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/expos")
@RequiredArgsConstructor
public class ExpoController {

    private final ExpoService expoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExpoResponse> createExpo(@Valid @RequestBody CreateExpoRequest request) {
        AuthenticatedUser user = getOrganizer();
        return ApiResponse.ok(expoService.create(user.userId(), request));
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
