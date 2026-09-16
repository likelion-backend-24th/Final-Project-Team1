package com.team1.reservation.round.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;


/**
 * 회차 수정(S9-2). <b>네 값을 모두 보낸다</b> - 조건부 UPDATE 로 통째로 덮어쓰기 때문이다.
 * fee 를 생략 가능하게 두면 안 보냈을 때 참가비가 조용히 0 이 된다.
 */
public record UpdateRoundRequest(

        @NotNull Instant startsAt,

        @NotNull Instant endsAt,

        @NotNull @Min(1) Integer capacity,

        @NotNull @Min(0) Integer fee) {
}
