package com.team1.reservation.reservation.service;

import com.team1.reservation.reservation.entity.TicketDispatch;
import com.team1.reservation.reservation.repository.TicketDispatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** 즉시 시도에 실패한 통지를 회수하는 배치(#79). */
@Service
public class TicketDispatchService {

    private static final Logger log = LoggerFactory.getLogger(TicketDispatchService.class);

    private final TicketDispatchRepository queue;
    private final TicketDispatcher dispatcher;
    private final Clock clock;
    private final int batchSize;

    public TicketDispatchService(TicketDispatchRepository queue,
                                 TicketDispatcher dispatcher,
                                 Clock clock,
                                 @Value("${scheduler.ticket-dispatch.batch-size}") int batchSize) {
        this.queue = queue;
        this.dispatcher = dispatcher;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /** 재시도 시각이 된 통지를 보낸다. 반환값은 이번 주기에 성공한 건수다. */
    public int dispatchDue() {
        Instant now = clock.instant();
        List<TicketDispatch> due = queue.findDue(now, PageRequest.of(0, batchSize));

        int succeeded = 0;
        for (TicketDispatch dispatch : due) {
            // dispatch() 는 예외를 삼키지만, Transaction 경계에서 나는 것까지 막지는 못한다.
            try {
                if (dispatcher.dispatch(dispatch.getId())) {
                    succeeded++;
                }
            } catch (RuntimeException e) {
                log.error("TICKET_DISPATCH_BATCH_FAILED dispatchId={} reason={}",
                        dispatch.getId(), e.toString());
            }
        }

        if (!due.isEmpty()) {
            log.info("ticket dispatch cycle: due={} succeeded={} now={}", due.size(), succeeded, now);
        }
        return succeeded;
    }
}
