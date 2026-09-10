package com.team1.reservation.reservation.service;

import com.team1.reservation.config.AfterCommitExecutor;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.TicketDispatch;
import com.team1.reservation.reservation.entity.TicketDispatchType;
import com.team1.reservation.reservation.repository.TicketDispatchRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/*
 확정된 예약의 티켓 발급을 Ticket-Service 에 통지한다(#79). */
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

    /** 예약 확정 → 티켓 발급 통지. */
    public void notifyIssued(Reservation reservation) {
        enqueueAndDispatch(reservation, TicketDispatchType.ISSUE);
    }

    /**
     * 예약 취소 → 티켓 무효화 통지.
     *
     * <p>발급보다 재시도가 더 중요하다. 무효화가 실패하면 취소된 예약으로 입장이 가능해진다.
     */
    public void notifyRevoked(Reservation reservation) {
        enqueueAndDispatch(reservation, TicketDispatchType.REVOKE);
    }

    private void enqueueAndDispatch(Reservation reservation, TicketDispatchType type) {
        TicketDispatch dispatch = enqueue(reservation, type);

        // 즉시 시도는 커밋 이후여야 한다. 롤백되는 Transaction 안에서 외부 호출을 하면
        // 티켓은 발급됐는데 예약은 사라지는 상태가 생긴다.
        afterCommit.execute(() -> dispatcher.dispatch(dispatch.getId()));
    }

    private TicketDispatch enqueue(Reservation reservation, TicketDispatchType type) {
        return queue.findByReservationIdAndType(reservation.getId(), type)
                .orElseGet(() -> queue.save(newDispatch(reservation, type)));
    }

    private TicketDispatch newDispatch(Reservation reservation, TicketDispatchType type) {
        Instant now = clock.instant();
        if (type == TicketDispatchType.REVOKE) {
            return TicketDispatch.revoke(reservation.getId(), reservation.getExpoId(),
                    reservation.getRoundId(), reservation.getUserId(), reservation.getHeadcount(), now);
        }
        return TicketDispatch.issue(reservation.getId(), reservation.getExpoId(),
                reservation.getRoundId(), reservation.getUserId(), reservation.getHeadcount(), now);
    }
}
