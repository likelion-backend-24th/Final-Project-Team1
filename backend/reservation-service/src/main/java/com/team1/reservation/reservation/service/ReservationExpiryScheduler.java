package com.team1.reservation.reservation.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**끄고 켜는 스위치를 둬서 Test 와 로컬에서 돌지 않게 한다. */
@Component
@ConditionalOnProperty(name = "reservation.expiry.enabled", havingValue = "true")
public class ReservationExpiryScheduler {

    private final ReservationExpiryService expiryService;

    public ReservationExpiryScheduler(ReservationExpiryService expiryService) {
        this.expiryService = expiryService;
    }

    // fixedDelay 다. fixedRate 로 두면 한 주기가 밀렸을 때 배치가 겹쳐 돈다.
    @Scheduled(fixedDelayString = "${reservation.expiry.interval}")
    public void run() {
        expiryService.expireDue();
    }
}
