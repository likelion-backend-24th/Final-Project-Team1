package com.team1.ticket.ticket.dto;


// 체크인 현황 집계 항목 (#121, Story 8). 박람회-Service 가 예약 현황 화면에 합친다.
// checkedIn 은 체크인 완료 "인원" = USED 티켓의 headcount 합. 예약 현황의 확정·취소와 같은 인원 단위.
// (예약당 티켓 1건 = 입장 1회에 headcount 명 전원 입장이므로, 건수가 아니라 인원으로 집계한다.)
public record CheckinSummaryItem(Long roundId, long checkedIn) {
}
