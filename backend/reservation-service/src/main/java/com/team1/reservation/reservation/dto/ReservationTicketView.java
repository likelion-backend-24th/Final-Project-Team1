package com.team1.reservation.reservation.dto;

import com.team1.reservation.client.TicketDetail;

import java.time.Instant;

/** 예약 상세에 실리는 QR 정보. */
public record ReservationTicketView(Long ticketId, String checkinToken, Instant issuedAt, String status) {

    public static ReservationTicketView from(TicketDetail ticket) {
        return new ReservationTicketView(
                ticket.ticketId(), ticket.checkinToken(), ticket.issuedAt(), ticket.status());
    }
}
