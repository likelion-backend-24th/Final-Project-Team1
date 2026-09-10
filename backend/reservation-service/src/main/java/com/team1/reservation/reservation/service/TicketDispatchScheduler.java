package com.team1.reservation.reservation.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** #79 재시도 배치의 시계. 끄고 켜는 스위치를 둬서 Test 와 로컬에서 돌지 않게 한다. */
@Component
@ConditionalOnProperty(name = "scheduler.ticket-dispatch.enabled", havingValue = "true")
public class TicketDispatchScheduler {

    private final TicketDispatchService dispatchService;

    public TicketDispatchScheduler(TicketDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(cron = "${scheduler.ticket-dispatch.cron}")
    public void run() {
        dispatchService.dispatchDue();
    }
}
