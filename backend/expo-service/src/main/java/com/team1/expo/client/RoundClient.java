package com.team1.expo.client;

import com.team1.expo.expo.dto.RoundView;

import java.util.List;

/**
 * reservation-service의 회차 내부 API 호출 창구.
 * 실패(연결 실패·Timeout·5xx)는 BusinessException(DEPENDENCY_UNAVAILABLE)으로 던지며 재시도하지 않는다.
 */
public interface RoundClient {

    /** GET /internal/v1/rounds/exists?expoId= — 공개 전환 전 회차 존재 확인용. */
    boolean existsByExpo(Long expoId);

    /** GET /internal/v1/rounds?expoId= — 박람회 상세의 회차·잔여 정원 병합용. */
    List<RoundView> listByExpo(Long expoId);
}
