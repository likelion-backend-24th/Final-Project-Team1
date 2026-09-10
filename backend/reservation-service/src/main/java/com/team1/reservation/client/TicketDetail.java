package com.team1.reservation.client;

import java.time.Instant;

/** 계약 1 의 티켓 단건 조회 응답. status 는 ISSUED·USED·CANCELLED. */
public record TicketDetail(Long ticketId, String checkinToken, Instant issuedAt, String status) {
}
