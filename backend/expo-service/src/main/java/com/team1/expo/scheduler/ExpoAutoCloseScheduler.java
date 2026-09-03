package com.team1.expo.scheduler;

import com.team1.expo.client.RoundClient;
import com.team1.expo.domain.expo.ExpoRepository;
import com.team1.expo.domain.expo.ExpoStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExpoAutoCloseScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpoAutoCloseScheduler.class);

    private final RoundClient roundClient;
    private final ExpoRepository expoRepository;
    private final Clock clock;

    @Value("${scheduler.auto-close.limit:500}")
    private int limit;

    @Scheduled(cron = "${scheduler.auto-close.cron}")
    @Transactional
    public void closeFinishedExpos() {
        Instant now = clock.instant();
        List<Long> expoIds;

        try {
            expoIds = roundClient.finishedExpoIds(now, limit);
        } catch (Exception e) {
            log.warn("[AutoClose] finishedExpoIds 호출 실패, 이번 주기 건너뜀", e);
            return;
        }

        if (expoIds.isEmpty()) {
            return;
        }

        LocalDateTime closedAt = LocalDateTime.ofInstant(now, clock.getZone());
        int updated = expoRepository.closeByIds(expoIds, ExpoStatus.PUBLISHED, ExpoStatus.CLOSED, closedAt);

        log.info("[AutoClose] 마감 처리 완료 대상={} 실제변경={} limit={}", expoIds.size(), updated, limit);

        if (expoIds.size() >= limit) {
            log.info("[AutoClose] limit({})에 도달 — 다음 주기에 남은 대상 처리", limit);
        }
    }
}
