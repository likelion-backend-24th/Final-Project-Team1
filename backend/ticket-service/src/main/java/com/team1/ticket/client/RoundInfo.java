package com.team1.ticket.client;

import java.time.Instant;


// 예약-Service 의 InternalRoundResponse 중 체크인 시간창에 필요한 부분만 받는다.
// 나머지 필드(capacity·remaining·fee)는 Jackson 이 무시한다.
public record RoundInfo(Long roundId, Instant startsAt, Instant endsAt) {
}
