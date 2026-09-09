package com.team1.reservation.reservation.dto;

import com.team1.reservation.reservation.entity.ReservationStatus;


//회차·상태별 예약 인원 집계. Repository 의 group by 결과를 담는 중간 타입이다.

public record RoundStatusHeadcount(Long roundId, ReservationStatus status, long headcount) {
}
