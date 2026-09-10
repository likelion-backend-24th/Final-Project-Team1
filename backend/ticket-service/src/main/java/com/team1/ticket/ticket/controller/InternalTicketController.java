package com.team1.ticket.ticket.controller;

import com.team1.ticket.ticket.dto.CheckinSummaryItem;
import com.team1.ticket.ticket.dto.IssueTicketsRequest;
import com.team1.ticket.ticket.dto.IssuedTicketResponse;
import com.team1.ticket.ticket.dto.TicketDetailResponse;
import com.team1.ticket.ticket.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


// 계약 1 (예약 ↔ 티켓). Reservation-Service(A) 가 호출하는 서비스간 내부 API.
// 내부 경로는 /internal/v1/... 로 통일(API 계약 확정사항 #2·#9).
// InternalAuthFilter 가 /internal/* 에 대해 Bearer internal token 을 검증한다.
@RestController
@RequestMapping("/internal/v1/tickets")
public class InternalTicketController {

    private final TicketService ticketService;

    public InternalTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    // 예약 확정 → 티켓 1건 발급 (예약당 1건, 멱등)
    // 성공 응답은 raw 로 내보낸다(봉투 없음). 예약(A) 클라이언트가 raw 로 역직렬화하고,
    // expo·reservation 내부 API 도 raw 다. 에러만 GlobalExceptionHandler 가 봉투로 감싼다.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedTicketResponse issue(@Valid @RequestBody IssueTicketsRequest request) {
        return ticketService.issue(request);
    }

    // 예약별 티켓 단건 조회 → 예약 상세 화면의 QR 표시(#82). 성공은 raw, 티켓 없으면 404.
    // status 를 포함해 화면이 ISSUED(QR)/USED(입장 완료)/CANCELLED(무효)를 구분한다.
    @GetMapping("/reservation/{reservationId}")
    public TicketDetailResponse getByReservation(@PathVariable Long reservationId) {
        return ticketService.getByReservation(reservationId);
    }

    // 체크인 현황 집계 (#121, Story 8). 박람회-Service 가 예약 현황 화면에 합치려고 호출. raw 응답.
    // 회차별 체크인 완료 인원. 체크인 0인 회차는 목록에 없으며 호출측이 0으로 병합한다.
    @GetMapping("/checkin-summary")
    public List<CheckinSummaryItem> checkinSummary(@RequestParam Long expoId) {
        return ticketService.getCheckinSummary(expoId);
    }

    // 예약 취소 → 해당 예약 티켓 무효화 (멱등). 계약상 204 No Content.
    @PatchMapping("/reservation/{reservationId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long reservationId) {
        ticketService.revokeByReservation(reservationId);
    }
}
