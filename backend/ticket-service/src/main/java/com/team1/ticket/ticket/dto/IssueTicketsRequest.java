package com.team1.ticket.ticket.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;


// 계약 1 (예약 → 티켓 발급). A(예약 Core)가 결제 확정 시 호출한다.
// 예약당 티켓 1건(API 계약 v3 #17). headcount 는 티켓 수가 아니라 이 티켓 1건이
// 대응하는 입장 인원(N명분)이며, 티켓에 저장돼 현장 체크인 인원 판단에 쓰인다.
public record IssueTicketsRequest(
        @NotNull Long reservationId,
        @NotNull Long expoId,
        @NotNull Long roundId,
        @NotNull Long userId,
        @Min(value = 1, message = "headcount must be at least 1") int headcount
) {
}
