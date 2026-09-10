package com.team1.ticket.client;


// 박람회-Service getExpoInternal 응답. 체크인 소유권 검증에 channelOwnerId 를 쓴다.
public record ExpoSummary(Long expoId, Long channelOwnerId, String status) {
}
