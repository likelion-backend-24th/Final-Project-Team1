package com.team1.reservation.reservation.service;

import com.team1.reservation.client.ExpoClient;
import com.team1.reservation.client.ExpoSummary;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.dto.CreateReservationRequest;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Service
public class ReservationService {

    static final String ROLE_MEMBER = "USER";

    /** 자리를 붙들고 있는 상태. 재예약 가능 여부와 정원 반환 판정의 기준이다. */
    static final Set<ReservationStatus> ACTIVE =
            EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);

    private static final String EXPO_OPEN_STATUS = "PUBLISHED";

    private final ReservationRepository reservations;
    private final RoundRepository rounds;
    private final ExpoClient expoClient;
    private final ReservationNoGenerator reservationNos;
    private final Clock clock;

    public ReservationService(ReservationRepository reservations,
                              RoundRepository rounds,
                              ExpoClient expoClient,
                              ReservationNoGenerator reservationNos,
                              Clock clock) {
        this.reservations = reservations;
        this.rounds = rounds;
        this.expoClient = expoClient;
        this.reservationNos = reservationNos;
        this.clock = clock;
    }


     //예약 신청. 정원 차감과 예약 저장을 한 Transaction 으로 묶는다.

    @Transactional
    public Reservation create(Long roundId, AuthenticatedUser user, CreateReservationRequest request) {
        requireMember(user);

        Instant now = clock.instant();
        Round round = rounds.findById(roundId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "round not found: " + roundId));

        // 종료된 회차·마감된 박람회는 "없는 것" 으로 취급한다. 존재 여부를 흘리지 않기 위해 404 다.
        if (!round.getEndsAt().isAfter(now)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "round has already ended: " + roundId);
        }
        ExpoSummary expo = expoClient.getExpo(round.getExpoId());
        if (!EXPO_OPEN_STATUS.equals(expo.status())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "expo is not open for reservation: " + round.getExpoId());
        }

        if (reservations.existsByRoundIdAndUserIdAndStatusIn(roundId, user.userId(), ACTIVE)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESERVATION,
                    "an active reservation already exists for round " + roundId);
        }

        int headcount = request.headcount();
        int amount = Math.multiplyExact(headcount, round.getFee());

        // reserve() 는 clearAutomatically 로 영속성 컨텍스트를 비운다. round 가 준영속이 되므로
        // 뒤에서 필요한 값은 미리 꺼내 둔다.
        Long expoId = round.getExpoId();

        // 부분 예약을 만들지 않는다. 5명 요청에 3자리가 남았으면 0명 예약이고 409 다.
        if (rounds.reserve(roundId, headcount) == 0) {
            throw new ApiException(ErrorCode.CAPACITY_EXCEEDED,
                    "not enough capacity on round " + roundId + " for " + headcount);
        }

        Reservation reservation = Reservation.create(
                reservationNos.generate(),
                roundId,
                expoId,
                user.userId(),
                request.normalizedName(),
                request.normalizedPhone(),
                headcount,
                amount,
                now);

        return reservations.save(reservation);
    }

    private void requireMember(AuthenticatedUser user) {
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "authentication required");
        }
        if (!ROLE_MEMBER.equals(user.role())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "USER role required");
        }
    }
}
