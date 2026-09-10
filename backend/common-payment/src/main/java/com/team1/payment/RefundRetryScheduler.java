package com.team1.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "scheduler.refund-retry.enabled", havingValue = "true")
public class RefundRetryScheduler {

    private final RefundRetryService retryService;

    public RefundRetryScheduler(RefundRetryService retryService){
        this.retryService = retryService;
    }

    @Scheduled(cron = "${scheduler.refund-retry.cron}")
    public void run(){
        retryService.retryDue();
    }
}
