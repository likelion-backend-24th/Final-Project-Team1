package com.team1.reservation.client;

/** headcount 는 티켓 수를 정하지 않는다. 발급 단위는 예약당 1건이고 이 값은 참고용이다. */
public record IssueTicketCommand(Long reservationId, Long expoId, Long roundId, Long userId, int headcount) {
}
