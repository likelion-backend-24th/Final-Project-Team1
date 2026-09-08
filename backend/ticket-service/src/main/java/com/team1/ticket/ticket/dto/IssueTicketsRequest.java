package com.team1.ticket.ticket.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;


// 계약 1 (예약 → 티켓 발급). A(예약 Core)가 결제 확정 시 호출한다.
// headcount 인원수만큼 티켓을 발급하며, 티켓당 QR(체크인 토큰) 1개를 가진다.
public record IssueTicketsRequest(
        @NotNull Long reservationId,
        @NotNull Long expoId,
        @NotNull Long roundId,
        @NotNull Long userId,
        @Min(value = 1, message = "headcount must be at least 1") int headcount
) {
}
