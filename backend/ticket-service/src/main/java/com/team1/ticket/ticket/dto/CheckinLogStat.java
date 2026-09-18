package com.team1.ticket.ticket.dto;

/**
 * 체크인 이력 집계 한 줄. {@code bucket} 은 집계 기준값이다.
 *
 * <p>시간대 집계면 시(0~23), 처리 방법 집계면 QR·RESERVATION_NO·UNKNOWN 이 들어온다.
 */
public record CheckinLogStat(String bucket, long count) {
}
