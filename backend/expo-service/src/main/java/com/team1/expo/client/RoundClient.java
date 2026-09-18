package com.team1.expo.client;

import com.team1.expo.expo.dto.ExpoFeeView;
import com.team1.expo.expo.dto.NearestDeadlineView;
import com.team1.expo.expo.dto.RoundView;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * reservation-service의 회차 내부 API 호출 창구.
 * 실패(연결 실패·Timeout·5xx)는 BusinessException(DEPENDENCY_UNAVAILABLE)으로 던지며 재시도하지 않는다.
 */
public interface RoundClient {

    /** GET /internal/v1/rounds/exists?expoId= — 공개 전환 전 회차 존재 확인용. */
    boolean existsByExpo(Long expoId);

    /** GET /internal/v1/rounds?expoId= — 박람회 상세의 회차·잔여 정원 병합용. */
    List<RoundView> listByExpo(Long expoId);

    /** GET /internal/v1/rounds/fee-summary?expoIds= — 목록 유료/무료 배지용 일괄 조회. */
    List<ExpoFeeView> feeSummaries(List<Long> expoIds);

    /** GET /internal/v1/rounds/finished-expos?before= — 자동 마감 대상 expoId 목록. */
    List<Long> finishedExpoIds(Instant before, int limit);

    /** GET /internal/v1/rounds/nearest-deadlines?expoIds= — 모집마감일순 정렬용 일괄 조회. */
    Map<Long, Instant> nearestDeadlines(List<Long> expoIds);
}
