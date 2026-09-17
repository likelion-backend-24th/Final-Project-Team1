package com.team1.ticket.ticket.dto;

import com.team1.ticket.ticket.entity.Ticket;

import java.time.Instant;


// 체크인 조회(verify) 응답. 주최자가 확정 전에 확인하는 티켓 정보.
// 예약자명 등 예약 소유 정보는 담지 않는다(Ticket-Service 소유가 아님).
//
// expoTitle·roundSequence 는 현장에서 사람이 읽는 값이다. 어느 쪽도 Ticket-Service 소유가
// 아니라 조회에 실패하면 null 이 되고, 화면은 그때 roundId 로 물러난다.
public record CheckinTicketView(
        Long ticketId,
        String status,
        String reservationNo,
        Long expoId,
        String expoTitle,
        Long roundId,
        Integer roundSequence,
        int headcount,
        Instant issuedAt,
        Instant usedAt) {

    public static CheckinTicketView from(Ticket ticket, String expoTitle, Integer roundSequence) {
        return new CheckinTicketView(
                ticket.getId(),
                ticket.getStatus().name(),
                ticket.getReservationNo(),
                ticket.getExpoId(),
                expoTitle,
                ticket.getRoundId(),
                roundSequence,
                ticket.getHeadcount(),
                ticket.getIssuedAt(),
                ticket.getUsedAt());
    }
}
