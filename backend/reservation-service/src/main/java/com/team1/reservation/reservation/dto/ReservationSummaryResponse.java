package com.team1.reservation.reservation.dto;

 //회차별 예약 현황. 박람회-Service 의 주최자 화면이 쓴다.

public record ReservationSummaryResponse(Long roundId,
                                         int capacity,
                                         int confirmed,
                                         int cancelled) {
}
