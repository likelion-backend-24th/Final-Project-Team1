package com.team1.ticket.ticket.dto;

import com.team1.ticket.ticket.entity.Ticket;

import java.time.Instant;


// 체크인 조회(verify) 응답. 주최자가 확정 전에 확인하는 티켓 정보.
// 예약자명 등 예약 소유 정보는 담지 않는다(Ticket-Service 소유가 아님).
public record CheckinTicketView(
        Long ticketId,
        String status,
        Long roundId,
        int headcount,
        Instant issuedAt,
        Instant usedAt) {

    public static CheckinTicketView from(Ticket ticket) {
        return new CheckinTicketView(
                ticket.getId(),
                ticket.getStatus().name(),
                ticket.getRoundId(),
                ticket.getHeadcount(),
                ticket.getIssuedAt(),
                ticket.getUsedAt());
    }
}
