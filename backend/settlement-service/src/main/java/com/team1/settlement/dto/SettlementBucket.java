package com.team1.settlement.dto;

/**
 * 기간을 잘게 쪼갠 한 칸. DAY·WEEK·MONTH 는 하루 단위(label="2026-09-23"),
 * YEAR 는 달 단위(label="2026-09")로 쪼갠다 - 화면이 달력·추이그래프를 이 배열 하나로 그린다.
 */
public record SettlementBucket(String label, long revenue, long refund, long net) {
}
