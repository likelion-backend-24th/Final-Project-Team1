package com.team1.reservation.client;

/** Ticket-Service 호출을 감춘 인터페이스. 발급 통지는 fail-open 이므로 예외는 호출자가 삼킨다. */
public interface TicketClient {

    IssuedTicket issueTicket(IssueTicketCommand command);
}
