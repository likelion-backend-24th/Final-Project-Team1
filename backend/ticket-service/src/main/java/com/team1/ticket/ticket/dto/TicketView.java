package com.team1.ticket.ticket.dto;

import com.team1.ticket.ticket.entity.Ticket;

import java.time.Instant;


public record TicketView(Long ticketId, String status, String checkinToken, Instant issuedAt) {

    public static TicketView from(Ticket ticket) {
        return new TicketView(
                ticket.getId(),
                ticket.getStatus().name(),
                ticket.getCheckinToken(),
                ticket.getIssuedAt());
    }
}
