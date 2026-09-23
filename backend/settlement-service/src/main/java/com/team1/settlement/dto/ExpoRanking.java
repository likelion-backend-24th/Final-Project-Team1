package com.team1.settlement.dto;

/** 박람회별 매출 랭킹 한 줄. title 조회 실패 시 "" 로 내려간다(금액은 항상 정확해야 한다). */
public record ExpoRanking(Long expoId, String title, long revenue) {
}
