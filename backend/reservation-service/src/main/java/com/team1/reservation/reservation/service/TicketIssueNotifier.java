package com.team1.reservation.reservation.service;

import com.team1.reservation.config.AfterCommitExecutor;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.TicketDispatch;
import com.team1.reservation.reservation.repository.TicketDispatchRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 확정된 예약의 티켓 발급을 Ticket-Service 에 통지한다(#79).
 *
 * <p>fail-open 이다. 통지가 실패해도 예약은 CONFIRMED 로 남는다 - 돈을 받아 놓고 자리를
 * 되돌리는 것보다, 티켓을 나중에 다시 발급하는 쪽이 언제나 낫기 때문이다.
 *
 * <p>그래서 통지 대상을 <b>확정과 같은 Transaction 에</b> 큐로 적재한다. 실패했을 때만
 * 적재하면 커밋과 통지 사이에 프로세스가 죽는 순간 통지가 통째로 유실된다. 적재해 두면
 * 즉시 시도가 어떻게 되든 배치가 회수한다.
 *
 * <p>재시도가 안전한 근거는 Ticket-Service 의 {@code tickets.reservation_id} UNIQUE 다.
 * 재호출하면 기존 티켓을 돌려준다(확정사항 #17).
 */
@Component
public class TicketIssueNotifier {

    private final TicketDispatchRepository queue;
    private final TicketDispatcher dispatcher;
    private final AfterCommitExecutor afterCommit;
    private final Clock clock;

    public TicketIssueNotifier(TicketDispatchRepository queue,
                               TicketDispatcher dispatcher,
                               AfterCommitExecutor afterCommit,
                               Clock clock) {
        this.queue = queue;
        this.dispatcher = dispatcher;
        this.afterCommit = afterCommit;
        this.clock = clock;
    }

    public void notifyIssued(Reservation reservation) {
        TicketDispatch dispatch = enqueue(reservation);

        // 즉시 시도는 커밋 이후여야 한다. 롤백되는 Transaction 안에서 외부 호출을 하면
        // 티켓은 발급됐는데 예약은 사라지는 상태가 생긴다.
        afterCommit.execute(() -> dispatcher.dispatch(dispatch.getId()));
    }

    private TicketDispatch enqueue(Reservation reservation) {
        return queue.findByReservationId(reservation.getId())
                .orElseGet(() -> queue.save(TicketDispatch.pending(
                        reservation.getId(),
                        reservation.getExpoId(),
                        reservation.getRoundId(),
                        reservation.getUserId(),
                        reservation.getHeadcount(),
                        clock.instant())));
    }
}
