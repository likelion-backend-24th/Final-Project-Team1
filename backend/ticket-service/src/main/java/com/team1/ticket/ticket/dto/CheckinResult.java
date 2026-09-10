package com.team1.ticket.ticket.dto;

import com.team1.ticket.ticket.entity.Ticket;

import java.time.Instant;


// 체크인 확정(checkin) 응답. USED 전이 결과.
public record CheckinResult(Long ticketId, String status, Instant checkedInAt) {

    public static CheckinResult from(Ticket ticket) {
        return new CheckinResult(ticket.getId(), ticket.getStatus().name(), ticket.getUsedAt());
    }
}
