package com.team1.reservation.reservation.service;

import com.team1.reservation.client.IssueTicketCommand;
import com.team1.reservation.client.IssuedTicket;
import com.team1.reservation.client.TicketClient;
import com.team1.reservation.common.TraceId;
import com.team1.reservation.reservation.entity.TicketDispatch;
import com.team1.reservation.reservation.entity.TicketDispatchStatus;
import com.team1.reservation.reservation.entity.TicketDispatchType;
import com.team1.reservation.reservation.repository.TicketDispatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * 통지 1건을 실제로 보낸다(#79). 즉시 시도와 재시도 배치가 모두 이 경로를 탄다.
 *
 * <p><b>절대 예외를 던지지 않는다.</b> 즉시 시도는 예약 확정 직후에 불리므로 여기서 예외가
 * 새어 나가면 fail-open 이 깨지고, 배치에서는 한 건이 나머지를 멈춘다.
 */
@Component
public class TicketDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TicketDispatcher.class);

    private final TicketDispatchRepository queue;
    private final TicketClient ticketClient;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration backoff;

    public TicketDispatcher(TicketDispatchRepository queue,
                            TicketClient ticketClient,
                            Clock clock,
                            @Value("${scheduler.ticket-dispatch.max-attempts}") int maxAttempts,
                            @Value("${scheduler.ticket-dispatch.backoff}") Duration backoff) {
        this.queue = queue;
        this.ticketClient = ticketClient;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
    }

    /** 성공하면 true. 실패·이미 처리됨은 false 이고, 어느 경우에도 예외를 던지지 않는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean dispatch(Long dispatchId) {
        TicketDispatch dispatch = queue.findById(dispatchId).orElse(null);
        if (dispatch == null || !dispatch.isPending()) {
            return false;
        }

        try {
            Long ticketId = send(dispatch);

            dispatch.succeeded(ticketId, clock.instant());
            log.info("ticket {} ok reservationId={} ticketId={} attempts={} traceId={}",
                    dispatch.getType(), dispatch.getReservationId(), ticketId,
                    dispatch.getAttempts(), TraceId.get());
            return true;

        } catch (RuntimeException e) {
            dispatch.failed(e.toString(), maxAttempts, backoff, clock.instant());
            logFailure(dispatch, e);
            return false;
        }
    }

    /** 무효화는 돌려받을 ticketId 가 없다(계약상 204). null 을 그대로 기록한다. */
    private Long send(TicketDispatch dispatch) {
        if (dispatch.getType() == TicketDispatchType.REVOKE) {
            ticketClient.revokeTicket(dispatch.getReservationId());
            return null;
        }
        IssuedTicket issued = ticketClient.issueTicket(new IssueTicketCommand(
                dispatch.getReservationId(), dispatch.getExpoId(), dispatch.getRoundId(),
                dispatch.getUserId(), dispatch.getHeadcount()));
        return issued.ticketId();
    }

    private void logFailure(TicketDispatch dispatch, RuntimeException e) {
        if (dispatch.getStatus() == TicketDispatchStatus.GAVE_UP) {
            // 자동 회수를 포기했다. CS 문의가 들어오면 이 로그로 찾는다.
            log.error("TICKET_DISPATCH_GAVE_UP type={} reservationId={} attempts={} traceId={} reason={}",
                    dispatch.getType(), dispatch.getReservationId(), dispatch.getAttempts(),
                    TraceId.get(), e.toString());
            return;
        }
        log.warn("TICKET_DISPATCH_FAILED type={} reservationId={} attempts={} nextAttemptAt={} traceId={} reason={}",
                dispatch.getType(), dispatch.getReservationId(), dispatch.getAttempts(),
                dispatch.getNextAttemptAt(), TraceId.get(), e.toString());
    }
}
