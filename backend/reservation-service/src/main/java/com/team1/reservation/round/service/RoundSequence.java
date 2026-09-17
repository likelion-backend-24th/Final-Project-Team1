package com.team1.reservation.round.service;

import com.team1.reservation.round.entity.Round;

import java.util.Comparator;
import java.util.List;

/**
 * 회차 번호(1회차·2회차)를 매기는 규칙 한 곳.
 *
 * <p>번호는 저장하지 않고 조회할 때마다 날짜순으로 다시 매긴다. 저장하면 회차를 추가·삭제할 때마다
 * 재계산해야 하고, 한 번 어긋나면 화면마다 다른 번호가 보인다.
 *
 * <p>같은 시각의 회차가 있어도 번호가 흔들리지 않게 id 로 한 번 더 가른다.
 * 단건 조회의 {@code countEarlierRounds} 쿼리도 이 순서와 정확히 같은 조건을 쓴다.
 */
public final class RoundSequence {

    // id 는 저장 전이면 null 이다. 정렬이 그 때문에 터지지 않게 뒤로 보낸다.
    public static final Comparator<Round> ORDER = Comparator
            .comparing(Round::getStartsAt)
            .thenComparing(Round::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private RoundSequence() {
    }

    public static List<Round> ordered(List<Round> rounds) {
        return rounds.stream().sorted(ORDER).toList();
    }
}
