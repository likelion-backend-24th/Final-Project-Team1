package com.team1.reservation.round.dto;

/**
 * 박람회 목록의 유료/무료 배지용 요약(계약 2 feeSummaries).
 * 예약 가능한 회차가 한 개도 없는 박람회는 아예 응답에 담기지 않는다 - 판정 근거가 없기 때문이다.
 */
public record ExpoFeeSummaryResponse(Long expoId, boolean paid) {
}
