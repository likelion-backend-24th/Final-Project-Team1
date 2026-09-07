package com.team1.ticket.ticket.dto;

import java.util.List;


public record IssuedTicketsResponse(Long reservationId, List<TicketView> tickets) {
}
