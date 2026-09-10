package com.team1.ticket.client;


// 박람회-Service 호출을 감춘 인터페이스. 체크인 소유권 검증(#7)에 쓴다.
public interface ExpoClient {

    ExpoSummary getExpo(Long expoId);
}
