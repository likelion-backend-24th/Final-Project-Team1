package com.team1.recommendation.internal.dto;

import com.team1.recommendation.activity.entity.EventType;

/**
 * 행동 이벤트.
 *
 * @param reservationId 예약 확정(RESERVATION_CONFIRMED)일 때만 채운다. 있으면 예약 확정 알림을 만든다.
 * @param reservationNo 알림 문구에 쓰는 예약번호. 없어도 된다.
 */
public record BehaviorEventRequest(Long userId, Long expoId, EventType eventType,
                                   Long reservationId, String reservationNo) {
}
