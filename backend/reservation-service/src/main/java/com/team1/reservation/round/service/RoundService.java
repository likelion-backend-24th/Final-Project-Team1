package com.team1.reservation.round.service;

import com.team1.reservation.client.ExpoClient;
import com.team1.reservation.client.ExpoSummary;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.round.dto.CreateRoundRequest;
import com.team1.reservation.round.dto.UpdateRoundRequest;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.security.AuthenticatedUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class RoundService {


    static final String ROLE_ORGANIZER = "ORGANIZER";


    public static final int MAX_FINISHED_EXPO_LIMIT = 1000;

    private final RoundRepository rounds;
    private final ExpoClient expoClient;
    private final Clock clock;

    public RoundService(RoundRepository rounds, ExpoClient expoClient, Clock clock) {
        this.rounds = rounds;
        this.expoClient = expoClient;
        this.clock = clock;
    }

    @Transactional
    public Round create(Long expoId, AuthenticatedUser user, CreateRoundRequest request) {

        Round round = Round.create(
                expoId,
                request.startsAt(),
                request.endsAt(),
                request.capacity(),
                request.feeOrZero(),
                clock.instant());

        requireOwnership(expoId, user);

        return rounds.save(round);
    }

    @Transactional(readOnly = true)
    public List<Round> listForOrganizer(Long expoId, AuthenticatedUser user) {
        requireOwnership(expoId, user);

        return rounds.findByExpoIdOrderByStartsAtAsc(expoId);
    }

    /**
     * 회차 일정·정원·참가비 수정(S9-2). 활성 예약이 한 건이라도 있으면 거절한다.
     *
     * <p>참가비를 바꿔도 이미 만들어진 예약의 결제 금액은 변하지 않는다 -
     * reservations.amount 는 신청 시점에 고정된 값이다. 애초에 활성 예약이 0건일 때만
     * 수정되므로 영향받을 예약도 없다.
     */
    @Transactional
    public Round update(Long expoId, Long roundId, AuthenticatedUser user, UpdateRoundRequest request) {
        requireOwnership(expoId, user);

        Round round = rounds.findById(roundId)
                .filter(r -> Objects.equals(r.getExpoId(), expoId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "round not found: " + roundId));

        Instant now = clock.instant();
        // 이미 시작한 회차는 손대지 않는다. 입장이 진행 중인데 시각·정원이 바뀌면 현장과 어긋난다.
        if (!round.getStartsAt().isAfter(now)) {
            throw new ApiException(ErrorCode.ROUND_ALREADY_STARTED,
                    "round has already started: " + roundId);
        }

        Round.validate(request.startsAt(), request.endsAt(), request.capacity(), request.fee(), now);

        // 읽고 판단하면 그 사이에 들어온 예약을 놓친다. 조건을 UPDATE 안에 둔다.
        int updated = rounds.updateIfNoReservation(roundId, request.startsAt(), request.endsAt(),
                request.capacity(), request.fee());
        if (updated == 0) {
            throw new ApiException(ErrorCode.ROUND_HAS_RESERVATIONS,
                    "round has active reservations: " + roundId);
        }

        // clearAutomatically 로 영속성 컨텍스트가 비워졌다. 갱신된 값을 다시 읽는다.
        return rounds.findById(roundId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "round not found: " + roundId));
    }

    // Ticket-Service 의 체크인 시간창 검증이 쓴다(계약 3-4).
    // 삭제 여부로 거르지 않는다 - 이미 발급된 티켓의 회차 시각을 확인하는 용도다.
    @Transactional(readOnly = true)
    public Round getById(Long roundId) {
        return rounds.findById(roundId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "round not found: " + roundId));
    }

    @Transactional(readOnly = true)
    public List<Round> listByExpo(Long expoId) {
        return rounds.findByExpoIdOrderByStartsAtAsc(expoId);
    }

    @Transactional(readOnly = true)
    public boolean existsByExpo(Long expoId) {
        return rounds.existsByExpoId(expoId);
    }

    @Transactional(readOnly = true)
    public List<Long> finishedExpoIds(Instant before, int limit) {
        int size = Math.min(Math.max(limit, 1), MAX_FINISHED_EXPO_LIMIT);
        return rounds.findExpoIdsWithAllRoundsEndedBefore(before, PageRequest.of(0, size));
    }


    private void requireOwnership(Long expoId, AuthenticatedUser user) {
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        if (!ROLE_ORGANIZER.equals(user.role())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "ORGANIZER role required");
        }

        ExpoSummary expo = expoClient.getExpo(expoId);
        if (!Objects.equals(expo.channelOwnerId(), user.userId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "not the owner of expo " + expoId);
        }
    }
}
