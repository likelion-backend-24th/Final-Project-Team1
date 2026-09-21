package com.team1.reservation.calendar.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalendarSuggestRateLimiterTest {

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;
    private static final Instant T0 = Instant.parse("2026-09-21T00:00:00Z");

    @Test
    @DisplayName("한도까지는 허용하고 그 다음은 거절한다")
    void allowsUpToLimitThenRejects() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(T0);
        CalendarSuggestRateLimiter limiter = new CalendarSuggestRateLimiter(3, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryAcquire(USER_A)).isTrue();
        assertThat(limiter.tryAcquire(USER_A)).isTrue();
        assertThat(limiter.tryAcquire(USER_A)).isTrue();
        assertThat(limiter.tryAcquire(USER_A)).isFalse();
    }

    @Test
    @DisplayName("다른 유저는 카운트가 섞이지 않는다")
    void doesNotMixCountsBetweenUsers() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(T0);
        CalendarSuggestRateLimiter limiter = new CalendarSuggestRateLimiter(1, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryAcquire(USER_A)).isTrue();
        assertThat(limiter.tryAcquire(USER_A)).isFalse();
        assertThat(limiter.tryAcquire(USER_B)).isTrue();
    }

    @Test
    @DisplayName("윈도우가 지나면 오래된 기록이 빠지고 다시 허용된다")
    void allowsAgainAfterWindowPasses() {
        Clock clock = mock(Clock.class);
        Instant afterWindow = T0.plus(Duration.ofMinutes(2));
        when(clock.instant()).thenReturn(T0, afterWindow);
        CalendarSuggestRateLimiter limiter = new CalendarSuggestRateLimiter(1, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryAcquire(USER_A)).isTrue();
        assertThat(limiter.tryAcquire(USER_A)).isTrue();
    }
}
