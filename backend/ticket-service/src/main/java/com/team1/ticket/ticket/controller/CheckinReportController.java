package com.team1.ticket.ticket.controller;

import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import com.team1.ticket.common.ApiException;
import com.team1.ticket.common.ApiResponse;
import com.team1.ticket.common.ErrorCode;
import com.team1.ticket.ticket.dto.CheckinReportResponse;
import com.team1.ticket.ticket.service.CheckinReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현장 체크인 결과 요약(#259). 주최자가 화면에서 "요약 보기" 를 누를 때만 호출된다.
 *
 * <p>자동 트리거를 두지 않는다 - 행사 종료 시점 판정은 박람회-Service 스케줄러의 몫이라
 * 여기서 끌어오면 소유가 흐려진다.
 */
@RestController
@RequestMapping("/api/v1/tickets")
public class CheckinReportController {

    private final CheckinReportService reportService;

    public CheckinReportController(CheckinReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/checkin-report")
    public ApiResponse<CheckinReportResponse> report(@RequestParam Long expoId) {
        return ApiResponse.ok(reportService.getReport(expoId, currentOrganizer()));
    }

    private AuthenticatedUser currentOrganizer() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "authentication required");
        }
        return user;
    }
}
