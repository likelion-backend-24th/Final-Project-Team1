package com.team1.reservation.round.dto;

import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.service.RoundSequence;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;


public record InternalRoundResponse(Long roundId,
                                    int sequence,
                                    Instant startsAt,
                                    Instant endsAt,
                                    int capacity,
                                    int remaining,
                                    int fee) {

    public static InternalRoundResponse from(Round round, int sequence) {
        return new InternalRoundResponse(
                round.getId(),
                sequence,
                round.getStartsAt(),
                round.getEndsAt(),
                round.getCapacity(),
                round.remaining(),
                round.getFee());
    }

    /** 목록은 정렬 순서가 곧 번호라 쿼리 없이 매긴다. */
    public static List<InternalRoundResponse> listOf(List<Round> rounds) {
        List<Round> ordered = RoundSequence.ordered(rounds);
        return IntStream.range(0, ordered.size())
                .mapToObj(i -> from(ordered.get(i), i + 1))
                .toList();
    }
}
