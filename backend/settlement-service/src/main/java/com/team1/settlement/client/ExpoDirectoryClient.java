package com.team1.settlement.client;

import java.util.Collection;
import java.util.Map;

/**
 * 정산의 박람회별·카테고리별 랭킹에 쓸 제목·카테고리 조회.
 * 실패해도 예외를 던지지 않고 빈 Map 을 돌려준다 - 이름을 못 붙여도 정산 금액 자체는 보여줘야 한다.
 */
public interface ExpoDirectoryClient {

    Map<Long, String> titles(Collection<Long> expoIds);

    Map<Long, String> categories(Collection<Long> expoIds);
}
