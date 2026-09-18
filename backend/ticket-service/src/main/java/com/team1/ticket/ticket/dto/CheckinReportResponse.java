package com.team1.ticket.ticket.dto;

import java.util.List;
import java.util.Map;

/**
 * 현장 체크인 결과 요약(#259).
 *
 * @param summary   LLM 이 쓴 요약 문장. 실패하면 null 이고 숫자는 그대로 내려간다
 * @param hourly    시간대별 입장 인원(한국 시간 기준). 입장이 없는 시간대는 빠진다
 * @param byMethod  처리 방법별 건수. 화면이 방법을 안 보냈으면 UNKNOWN
 * @param reverted  되돌린 체크인 건수
 */
public record CheckinReportResponse(Long expoId,
                                    String expoTitle,
                                    int reserved,
                                    int capacity,
                                    long checkedIn,
                                    long noShow,
                                    int checkinRate,
                                    List<HourlyCheckin> hourly,
                                    Map<String, Long> byMethod,
                                    long reverted,
                                    String summary) {

    public record HourlyCheckin(int hour, long count) {
    }
}
