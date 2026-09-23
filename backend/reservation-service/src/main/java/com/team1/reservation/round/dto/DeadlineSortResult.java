package com.team1.reservation.round.dto;

import java.util.List;

/** 모집마감일순 페이지 결과. 마감일 있는 박람회 먼저(오름차순), 없는 박람회는 끝에. */
public record DeadlineSortResult(List<Long> expoIds, long totalElements) {}
