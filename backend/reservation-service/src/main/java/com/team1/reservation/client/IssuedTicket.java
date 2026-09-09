package com.team1.reservation.client;

import java.time.Instant;

public record IssuedTicket(Long ticketId, String checkinToken, Instant issuedAt) {
}
