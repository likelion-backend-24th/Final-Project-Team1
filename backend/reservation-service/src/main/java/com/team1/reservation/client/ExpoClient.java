package com.team1.reservation.client;


public interface ExpoClient {


    ExpoSummary getExpo(Long expoId);

    /**
     * 박람회를 비공개로 되돌린다(계약 2-3). 마지막 회차를 삭제하기 <b>직전</b>에 부른다.
     * 멱등이며, 실패하면 예외를 던진다 - 호출부는 삭제를 중단해야 한다(fail-closed).
     */
    void unpublish(Long expoId);
}
