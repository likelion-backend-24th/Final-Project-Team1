package com.team1.reservation.reservation.entity;

/** 큐가 나르는 통지의 종류(#79). */
public enum TicketDispatchType {

    /** 예약 확정 → 티켓 발급. */
    ISSUE,

    /** 예약 취소 → 티켓 무효화. */
    REVOKE
}
