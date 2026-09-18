package com.team1.ticket.client;

import java.time.Instant;

/** 예약-Service 의 회차별 예약 현황. 체크인 요약(#259)이 "예약 대비 입장" 을 내는 데 쓴다. */
public record ReservationSummary(Long roundId,
                                 int capacity,
                                 int confirmed,
                                 int cancelled,
                                 Instant startsAt,
                                 Instant endsAt) {
}
