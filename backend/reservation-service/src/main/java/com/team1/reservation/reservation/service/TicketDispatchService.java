package com.team1.reservation.reservation.service;

import com.team1.reservation.reservation.entity.TicketDispatch;
import com.team1.reservation.reservation.entity.TicketDispatchStatus;
import com.team1.reservation.reservation.entity.TicketDispatchType;
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

    /** 최대 조회 건수. 화면이 limit 를 크게 넣어도 배치와 같은 규모를 넘지 않게 한다. */
    private static final int MAX_GAVE_UP_LIMIT = 500;

    /** 자동 회수를 포기한 통지. type 이 null 이면 전체. */
    public List<TicketDispatch> gaveUp(TicketDispatchType type, int limit) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), MAX_GAVE_UP_LIMIT));
        if (type == null) {
            return queue.findByStatusOrderByUpdatedAtAsc(TicketDispatchStatus.GAVE_UP, page);
        }
        return queue.findByStatusAndTypeOrderByUpdatedAtAsc(TicketDispatchStatus.GAVE_UP, type, page);
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
