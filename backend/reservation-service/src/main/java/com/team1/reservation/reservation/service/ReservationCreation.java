package com.team1.reservation.reservation.service;

import com.team1.reservation.reservation.entity.Reservation;

/**
 * 예약 생성 결과. 예약 엔티티만으로는 부족해서 별도 타입을 둔다 —
 * @param paymentId 무료 회차는 결제가 없으므로 {@code null} 이다
 */
public record ReservationCreation(Reservation reservation, String paymentId) {
}
