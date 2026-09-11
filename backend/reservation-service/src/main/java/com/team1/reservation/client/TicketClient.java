package com.team1.reservation.client;

/** Ticket-Service 호출을 감춘 인터페이스. 발급 통지는 fail-open 이므로 예외는 호출자가 삼킨다. */
public interface TicketClient {

    IssuedTicket issueTicket(IssueTicketCommand command);

    /** 계약 1-2. 발급 전이거나 이미 취소됐어도 성공한다(멱등). */
    void revokeTicket(Long reservationId);

    /** 아직 발급되지 않았거나 조회에 실패하면 null. 호출자가 부분 실패로 처리한다. */
    TicketDetail findTicket(Long reservationId);

    /**
     * 아직 발급되지 않았으면 null, <b>조회에 실패하면 예외</b>.
     * "모른다" 를 허용할 수 없는 경로가 쓴다 — 모른 채 취소하면 입장한 예약을 환불하게 된다.
     */
    TicketDetail findTicketFailClosed(Long reservationId);
}
