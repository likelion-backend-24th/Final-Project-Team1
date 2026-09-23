package com.team1.settlement.dto;

/** 정산 조회 단위. 각 값이 date 를 기준으로 어떤 범위를 잡을지는 SettlementService 가 정한다. */
public enum SettlementPeriod {
    DAY, WEEK, MONTH, YEAR
}
