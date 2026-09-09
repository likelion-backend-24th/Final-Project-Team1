package com.team1.reservation.reservation.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** #77 만료 배치의 시계. 끄고 켜는 스위치를 둬서 Test 와 로컬에서 돌지 않게 한다. */
@Component
@ConditionalOnProperty(name = "scheduler.reservation-expiry.enabled", havingValue = "true")
public class ReservationExpiryScheduler {

    private final ReservationExpiryService expiryService;

    public ReservationExpiryScheduler(ReservationExpiryService expiryService) {
        this.expiryService = expiryService;
    }

    // 주기를 유예(10분)보다 짧게 잡아, 보류된 건이 유예 창 안에서 여러 번 재확인된다.
    @Scheduled(cron = "${scheduler.reservation-expiry.cron}")
    public void run() {
        expiryService.expireDue();
    }
}
