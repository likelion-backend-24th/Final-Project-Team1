package com.team1.reservation.calendar.service;

import com.team1.reservation.calendar.dto.ScheduleConstraint;
import com.team1.reservation.round.dto.InternalRoundResponse;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/*
 * 시간 안겹치는 회차 조합을 고른다. 종료시각 순 그리디
 * - 이게 겹치치않게 최대한 많이담기 알고리즘(시작시각 순으로 하면 최적이 아닐 수 있음).
 *
 * <p>같은 박람회 회차가 여럿 뽑히는 걸 막되, 대표 회차를 사전에 정하지 않는다.
 * 루프중 이미 그 박람회를 담았으면 스킵 하는 방식, 대표 회차가 겹처서
 * 빠져도 그 박람회의 다른 회차가 대신 들어갈 수 있다.
 * */
public final class ScheduleGreedyPicker {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private ScheduleGreedyPicker(){
    }

    public static List<InternalRoundResponse> pick(List<InternalRoundResponse> candidates,
                                                   ScheduleConstraint constraint){

        List<InternalRoundResponse> satisfying = candidates.stream()
                .filter(r -> satisfies(r, constraint))
                .sorted(Comparator.comparing(InternalRoundResponse::endsAt)
                        .thenComparing(InternalRoundResponse::roundId))
                .toList();

        List<InternalRoundResponse> picked = new ArrayList<>();
        Set<Long> pickedExpoIds = new HashSet<>();
        Instant lastEnd = null;

        for (InternalRoundResponse round : satisfying){
            if (lastEnd != null && round.startsAt().isBefore(lastEnd)){
                continue;
            }
            if (pickedExpoIds.contains(round.expoId())){
                continue;
            }

            picked.add(round);
            pickedExpoIds.add(round.expoId());
            lastEnd = round.endsAt();
        }
        return picked;
    }

    private static boolean satisfies(InternalRoundResponse round, ScheduleConstraint constraint){
        if (constraint == null || constraint.mainStartHourKst()==null){
            return true;
        }
        int hour = round.startsAt().atZone(KST).getHour();
        return hour >= constraint.mainStartHourKst();
    }




}
