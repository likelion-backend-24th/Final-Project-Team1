package com.team1.ticket.ticket.controller;

import com.team1.ticket.common.ApiException;
import com.team1.ticket.common.ApiResponse;
import com.team1.ticket.common.ErrorCode;
import com.team1.ticket.ticket.dto.CheckinResult;
import com.team1.ticket.ticket.dto.CheckinTicketView;
import com.team1.ticket.ticket.service.TicketCheckinService;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


// 현장 체크인 외부 API (Story 7, #74). 주최자(브라우저)가 호출한다.
// 외부 경로이므로 성공 응답은 봉투(ApiResponse). JWT 는 SecurityConfig 의 필터가 /api/* 에서 검증한다.
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketCheckinController {

    private final TicketCheckinService checkinService;

    public TicketCheckinController(TicketCheckinService checkinService) {
        this.checkinService = checkinService;
    }

    // 스캔한 QR 의 체크인 토큰으로 티켓 조회 (미전이). 주최자가 확정 전에 확인한다.
    @GetMapping("/verify")
    public ApiResponse<CheckinTicketView> verify(@RequestParam String code) {
        return ApiResponse.ok(checkinService.verify(code, currentOrganizer()));
    }

    // 체크인 확정 → USED 전이.
    @PostMapping("/{ticketId}/checkin")
    public ApiResponse<CheckinResult> checkin(@PathVariable Long ticketId) {
        return ApiResponse.ok(checkinService.checkin(ticketId, currentOrganizer()));
    }

    private AuthenticatedUser currentOrganizer() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "authentication required");
        }
        return user;
    }
}
