package com.team1.settlement.dto;

/** 카테고리별 매출 랭킹 한 줄. 카테고리를 못 찾은 박람회는 "기타"로 묶는다. */
public record CategoryRanking(String category, long revenue) {
}
