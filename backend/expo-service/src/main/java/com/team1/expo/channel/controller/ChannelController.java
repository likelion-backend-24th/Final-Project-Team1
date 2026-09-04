package com.team1.expo.channel.controller;

import com.team1.expo.channel.dto.ChannelResponse;
import com.team1.expo.channel.dto.CreateChannelRequest;
import com.team1.expo.channel.service.ChannelService;
import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.common.response.ApiResponse;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChannelResponse> createChannel(
            @Valid @RequestBody CreateChannelRequest request) {
        AuthenticatedUser user = getCurrentOrganizer();
        return ApiResponse.ok(channelService.create(user.userId(), request));
    }

    @GetMapping("/my")
    public ApiResponse<ChannelResponse> getMyChannel() {
        AuthenticatedUser user = getCurrentOrganizer();
        return ApiResponse.ok(channelService.getMyChannel(user.userId()));
    }

    @GetMapping("/{channelId}")
    public ApiResponse<ChannelResponse> getChannel(@PathVariable Long channelId) {
        AuthenticatedUser user = getCurrentOrganizer();
        return ApiResponse.ok(channelService.getOne(channelId, user.userId()));
    }

    private AuthenticatedUser getCurrentOrganizer() {
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
