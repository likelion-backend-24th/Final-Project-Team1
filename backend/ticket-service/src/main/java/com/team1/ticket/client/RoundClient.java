package com.team1.ticket.client;


// 예약-Service 회차 조회. 체크인 가능 시간창 검증에만 쓴다.
public interface RoundClient {

    /**
     * 회차를 모르면 {@code null}. <b>실패를 예외로 올리지 않는다</b> - 시간창은 fail-open 이다.
     * 티켓 유효성·중복·소유권은 전부 로컬에서 끝나므로, 부가 검증 하나로 입장 줄을 멈추지 않는다.
     */
    RoundInfo findRound(Long roundId);
}
