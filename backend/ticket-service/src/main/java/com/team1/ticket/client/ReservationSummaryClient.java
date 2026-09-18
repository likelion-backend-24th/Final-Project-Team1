package com.team1.ticket.client;

import java.util.List;

/** 예약-Service 의 회차별 예약 현황 조회. */
public interface ReservationSummaryClient {

    /**
     * 실패하면 빈 목록. <b>예외로 올리지 않는다</b> - 예약 수를 못 받아도 체크인 집계는 낼 수 있고,
     * 요약 화면 하나 때문에 오류를 보여줄 이유가 없다.
     */
    List<ReservationSummary> findSummaries(Long expoId);
}
