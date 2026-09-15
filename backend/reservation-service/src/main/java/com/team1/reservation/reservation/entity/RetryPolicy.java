package com.team1.reservation.reservation.entity;

import java.time.Duration;

/**
 * 통지 재시도 정책(#79, S7-6). 발급과 무효화가 서로 다른 값을 쓴다.
 *
 * <p><b>왜 나누는가.</b> 발급 실패는 "티켓이 안 나옴" 이라 사용자가 알아채고 문의한다.
 * 무효화 실패는 "취소된 예약으로 입장 가능" 이고, <b>아무도 모르는 채 구멍이 열린 채로 남는다.</b>
 * 후자가 더 나쁘므로 훨씬 오래 시도한다.
 */
public record RetryPolicy(int maxAttempts, Duration backoff, Duration maxBackoff) {

    /**
     * 지수 백오프에 상한을 둔다. 상한이 없으면 뒤로 갈수록 간격이 배로 벌어져
     * 마지막 시도가 몇 시간 뒤가 되고, 그동안 구멍이 열린 채로 남는다.
     */
    public Duration delayFor(int attempts) {
        Duration delay = backoff.multipliedBy(1L << (attempts - 1));
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }
}
