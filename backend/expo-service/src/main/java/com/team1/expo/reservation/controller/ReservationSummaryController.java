package com.team1.expo.reservation.controller;

import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.common.response.ApiResponse;
import com.team1.expo.reservation.dto.ReservationSummaryResponse;
import com.team1.expo.reservation.service.ReservationSummaryService;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/expos")
@RequiredArgsConstructor
public class ReservationSummaryController {

    private final ReservationSummaryService summaryService;

    @GetMapping("/{expoId}/reservations/summary")
    public ApiResponse<ReservationSummaryResponse> getSummary(@PathVariable Long expoId) {
        AuthenticatedUser user = requireOrganizer();
        return ApiResponse.ok(summaryService.getSummary(user.userId(), expoId));
    }

    private AuthenticatedUser requireOrganizer() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!"ORGANIZER".equals(user.role())) throw new BusinessException(ErrorCode.FORBIDDEN);
        return user;
    }
}
