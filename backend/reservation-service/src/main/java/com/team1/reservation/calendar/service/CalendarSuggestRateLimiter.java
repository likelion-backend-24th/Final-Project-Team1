package com.team1.reservation.calendar.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 유저별 캘린더 추천 호출 빈도 제한. 인스턴스 안에서만 센다(AiCallBudget과 같은 전제 ㅡ
 * 서버 한 대 기준, 여러대로 늘어나면 공유 저장소로 옮겨야 한다).
 *
 * <p> 하루 무료 한도(AiCallBudget)와는 다른 문제 - 한사람이 새로고침 연타 막는용도.
 * */
@Component
public class CalendarSuggestRateLimiter {

    private final int maxPerWindow;
    private final Duration window;
    private final Clock clock;
    private final Map<Long, Deque<Instant>> requestsByUser = new ConcurrentHashMap<>();

    @Autowired
    public CalendarSuggestRateLimiter(Clock clock) {
        this(5, Duration.ofMinutes(1), clock);
    }

    CalendarSuggestRateLimiter(int maxPerWindow, Duration window, Clock clock){
        this.maxPerWindow = maxPerWindow;
        this.window = window;
        this.clock = clock;
    }

//    이번 요청을 허용하면 true. 허용된 요청만 타임스탬프를 남김.
    public synchronized  boolean tryAcquire(Long userId){
        Instant now = clock.instant();
        Deque<Instant> timestamps = requestsByUser.computeIfAbsent(userId, k -> new ArrayDeque<>());
        while(!timestamps.isEmpty() && timestamps.peekFirst().isBefore(now.minus(window))){
            timestamps.pollFirst();
        }
        if (timestamps.size() >= maxPerWindow){
            return false;
        }
        timestamps.addLast(now);
        return true;
    }


}
