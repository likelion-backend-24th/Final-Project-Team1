package com.team1.expo.expo.dto;

/**
 * reservation-service 의 ExpoFeeSummaryResponse 를 그대로 받는 뷰(계약 2 feeSummaries).
 * 예약 가능한 회차가 없는 박람회는 응답에 없으므로, 조회 결과에 없다 = 판정 불가다.
 */
public record ExpoFeeView(Long expoId, boolean paid) {
}
