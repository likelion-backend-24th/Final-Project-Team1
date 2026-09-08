package com.team1.reservation.reservation.entity;

/**
 * 예약 상태. DB 에는 VARCHAR(20) + CHECK 제약으로 저장한다.
 * <p>결제 실패는 {@link #CANCELLED}, 미결제 만료는 {@link #EXPIRED} 로 구분한다.
 * 둘 다 "예약이 성립하지 않음" 이지만 화면에 사유를 다르게 보여줘야 하기 때문이다.
 */
public enum ReservationStatus {

    /** 예약 신청 완료, 결제 대기. 정원은 이미 차감된 상태다. */
    PENDING,

    /** 결제 승인 완료. */
    CONFIRMED,

    /** 결제 실패(PENDING 에서) 또는 사용자 취소(CONFIRMED 에서). */
    CANCELLED,

    /** 결제 대기 시간이 지나 자동 만료. */
    EXPIRED
}
