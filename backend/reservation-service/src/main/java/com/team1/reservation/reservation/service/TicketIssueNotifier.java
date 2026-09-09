package com.team1.reservation.reservation.service;

import com.team1.reservation.client.IssueTicketCommand;
import com.team1.reservation.client.IssuedTicket;
import com.team1.reservation.client.TicketClient;
import com.team1.reservation.common.TraceId;
import com.team1.reservation.config.AfterCommitExecutor;
import com.team1.reservation.reservation.entity.Reservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


//확정된 예약을 Ticket-Service 에 통지한다(#79).

@Component
public class TicketIssueNotifier {

    private static final Logger log = LoggerFactory.getLogger(TicketIssueNotifier.class);

    private final TicketClient ticketClient;
    private final AfterCommitExecutor afterCommit;

    public TicketIssueNotifier(TicketClient ticketClient, AfterCommitExecutor afterCommit) {
        this.ticketClient = ticketClient;
        this.afterCommit = afterCommit;
    }

    public void notifyIssued(Reservation reservation) {
        // 커밋 뒤에는 Entity 가 준영속일 수 있으므로 값을 지금 복사해 둔다.
        IssueTicketCommand command = new IssueTicketCommand(
                reservation.getId(),
                reservation.getExpoId(),
                reservation.getRoundId(),
                reservation.getUserId(),
                reservation.getHeadcount());
        String traceId = TraceId.get();

        afterCommit.execute(() -> issue(command, traceId));
    }

    private void issue(IssueTicketCommand command, String traceId) {
        try {
            IssuedTicket issued = ticketClient.issueTicket(command);
            log.info("ticket issued reservationId={} ticketId={} traceId={}",
                    command.reservationId(), issued.ticketId(), traceId);

        } catch (RuntimeException e) {
            // 예약은 이미 커밋됐다. 실패를 남기고 넘어간 뒤 재통지로 회수한다.
            log.error("TICKET_ISSUE_FAILED reservationId={} roundId={} userId={} traceId={} reason={}",
                    command.reservationId(), command.roundId(), command.userId(), traceId, e.toString());
        }
    }
}
