package com.team1.reservation.client;

//박람회-service의 공개 박람회 목록 응답에서 id만 읽는다. title,description은 무시.
public record PublishedExpoId(Long expoId) {
}
