package com.team1.ticket.ticket.dto;

import com.team1.ticket.ticket.entity.Ticket;

import java.time.Instant;


// 예약당 티켓 1건(API 계약 v3 #17). 발급 결과는 단건이다.
public record IssuedTicketResponse(Long ticketId, String checkinToken, Instant issuedAt) {

    public static IssuedTicketResponse from(Ticket ticket) {
        return new IssuedTicketResponse(
                ticket.getId(),
                ticket.getCheckinToken(),
                ticket.getIssuedAt());
    }
}
