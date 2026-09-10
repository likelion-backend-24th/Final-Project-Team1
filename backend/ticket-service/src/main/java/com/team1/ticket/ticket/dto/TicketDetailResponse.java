package com.team1.ticket.ticket.dto;

import com.team1.ticket.ticket.entity.Ticket;

import java.time.Instant;


// 예약별 티켓 단건 조회 응답. 예약 상세 화면(QR 표시)이 status 로 분기한다:
// ISSUED→QR 표시, USED→"입장 완료", CANCELLED→무효. 그래서 status 를 반드시 포함한다.
public record TicketDetailResponse(Long ticketId, String checkinToken, Instant issuedAt, String status) {

    public static TicketDetailResponse from(Ticket ticket) {
        return new TicketDetailResponse(
                ticket.getId(),
                ticket.getCheckinToken(),
                ticket.getIssuedAt(),
                ticket.getStatus().name());
    }
}
