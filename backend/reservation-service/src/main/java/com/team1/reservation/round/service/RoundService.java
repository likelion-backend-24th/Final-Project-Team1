package com.team1.reservation.round.service;

import com.team1.reservation.client.ExpoClient;
import com.team1.reservation.client.ExpoSummary;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.round.dto.CreateRoundRequest;
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
