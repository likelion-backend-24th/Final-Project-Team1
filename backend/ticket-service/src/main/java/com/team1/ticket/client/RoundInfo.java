package com.team1.ticket.client;

import java.time.Instant;


// 예약-Service 의 InternalRoundResponse 중 체크인이 쓰는 부분만 받는다.
// 나머지 필드(capacity·remaining·fee)는 Jackson 이 무시한다.
// sequence 는 "몇 회차"를 화면에 보여주려고 함께 받는다.
public record RoundInfo(Long roundId, int sequence, Instant startsAt, Instant endsAt) {
}
