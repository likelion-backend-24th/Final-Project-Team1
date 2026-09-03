package com.team1.expo.expo.dto;

import java.time.Instant;

/**
 * reservation-service의 내부 회차 응답(InternalRoundResponse)을 그대로 받아
 * 박람회 상세에 병합해 노출하는 뷰. 잔여 정원(remaining)은 계산하지 않고 전달만 한다.
 */
public record RoundView(
        Long roundId,
        Instant startsAt,
        Instant endsAt,
        int capacity,
        int remaining
) {
}
