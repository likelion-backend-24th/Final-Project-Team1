package com.team1.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 하루 호출 상한. 무료 한도를 한 기능이 다 써버리는 것을 막는다.
 *
 * <p>인스턴스 안에서만 센다. 서버가 한 대이므로 이걸로 충분하고, 여러 대로 늘어나면
 * 공유 저장소로 옮겨야 한다. 상한을 넘겨도 예외로 서비스를 멈추지 않고 호출만 거절한다.
 */
public class AiCallBudget {

    private static final Logger log = LoggerFactory.getLogger(AiCallBudget.class);

    private final int dailyLimit;
    private final Clock clock;
    private final Map<String, AtomicInteger> countsByFeature = new ConcurrentHashMap<>();

    private volatile LocalDate today;

    public AiCallBudget(int dailyLimit, Clock clock) {
        this.dailyLimit = dailyLimit;
        this.clock = clock;
        this.today = currentDate();
    }

    /** 오늘 몫이 남아 있으면 한 건 당겨 쓰고 true. 상한을 넘겼으면 false. */
    public synchronized boolean tryAcquire(String feature) {
        rolloverIfNewDay();
        if (dailyLimit <= 0) return true;

        int used = totalUsed();
        if (used >= dailyLimit) {
            log.warn("ai daily limit reached feature={} used={} limit={}", feature, used, dailyLimit);
            return false;
        }
        countsByFeature.computeIfAbsent(feature, k -> new AtomicInteger()).incrementAndGet();
        return true;
    }

    public synchronized boolean hasRemaining() {
        rolloverIfNewDay();
        return dailyLimit <= 0 || totalUsed() < dailyLimit;
    }

    /** 기능별 오늘 사용량. 어디서 얼마나 쓰는지 확인하는 용도다. */
    public synchronized Map<String, Integer> usage() {
        rolloverIfNewDay();
        return countsByFeature.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    private void rolloverIfNewDay() {
        LocalDate now = currentDate();
        if (!now.equals(today)) {
            log.info("ai budget rollover date={} usage={}", today, usage0());
            countsByFeature.clear();
            today = now;
        }
    }

    private String usage0() {
        return countsByFeature.toString();
    }

    private int totalUsed() {
        return countsByFeature.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    private LocalDate currentDate() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
