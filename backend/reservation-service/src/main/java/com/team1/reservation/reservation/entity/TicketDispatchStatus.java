package com.team1.reservation.reservation.entity;

/** 티켓 발급 통지의 상태(#79). */
public enum TicketDispatchStatus {

    /** 아직 성공하지 못했다. 배치가 next_attempt_at 이후에 다시 집는다. */
    PENDING,

    SUCCEEDED,

    /** 재시도 상한을 넘겼다. 자동 회수를 포기했으므로 사람이 봐야 한다. */
    GAVE_UP
}
