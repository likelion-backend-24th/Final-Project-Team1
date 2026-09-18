package com.team1.reservation.reservation.service;

import com.team1.payment.PaymentTransaction;
import com.team1.reservation.client.ExpoClient;
import com.team1.reservation.client.TicketClient;
import com.team1.reservation.client.TicketDetail;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.dto.MyReservationDetailResponse;
import com.team1.reservation.reservation.dto.MyReservationResponse;
import com.team1.reservation.reservation.dto.ReservationTicketView;
import com.team1.reservation.reservation.entity.RefundState;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.repository.PaymentLookupRepository;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.reservation.round.service.RoundService;
import com.team1.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 회원 본인의 예약 조회(#82).
 *
 * <p>화면이 상태 조합표를 알 필요가 없도록 {@code refundState} 를 서버가 파생해 내려준다.
 * 파생은 {@link RefundState#of} 한 곳에서만 하며 취소 API 와 같은 값을 쓴다.
 */
@Service
public class MyReservationService {

    static final String ROLE_MEMBER = "USER";

    private final ReservationRepository reservations;
    private final RoundRepository rounds;
    private final PaymentLookupRepository payments;
    private final TicketClient ticketClient;
    private final ExpoClient expoClient;
    private final int refundMaxAttempts;
    private final RoundService roundService;

    public MyReservationService(ReservationRepository reservations,
                                RoundRepository rounds,
                                PaymentLookupRepository payments,
                                TicketClient ticketClient,
                                ExpoClient expoClient,
                                RoundService roundService,
                                @Value("${scheduler.refund-retry.max-attempts}") int refundMaxAttempts) {
        this.reservations = reservations;
        this.rounds = rounds;
        this.payments = payments;
        this.ticketClient = ticketClient;
        this.expoClient = expoClient;
        this.refundMaxAttempts = refundMaxAttempts;
        this.roundService = roundService;
    }

    @Transactional(readOnly = true)
    public List<MyReservationResponse> listMine(AuthenticatedUser user) {
        requireMember(user);

        List<Reservation> mine = reservations.findByUserIdOrderByCreatedAtDesc(user.userId());
        if (mine.isEmpty()) {
            return List.of();
        }

        // 목록에서 예약마다 회차·결제를 따로 조회하면 N+1 이 된다. 한 번에 당겨 Map 으로 맞춘다.
        Map<Long, Round> roundsById = roundsOf(mine);
        Map<Long, PaymentTransaction> paymentsByReservationId = paymentsOf(mine);
        Set<Long> expoIds = mine.stream().map(Reservation::getExpoId).collect(Collectors.toSet());
        Map<Long, String> titlesByExpoId = expoClient.titles(expoIds);
        Map<Long, Integer> sequencesByRoundId = roundService.sequencesOf(expoIds);

        return mine.stream()
                .map(reservation -> MyReservationResponse.of(
                        reservation,
                        roundsById.get(reservation.getRoundId()),
                        titlesByExpoId.get(reservation.getExpoId()),
                        sequencesByRoundId.get(reservation.getRoundId()),
                        refundStateOf(reservation, paymentsByReservationId.get(reservation.getId()))))
                .toList();
    }

//    /**
//     * 회차 번호는 그 박람회의 살아있는 회차를 전부 알아야 매길 수 있다 - 예약이 가리키는 회차만
//     * 모아서는 몇 번째인지 알 수 없다. 박람회 단위로 한 번에 당겨 번호를 붙인다.
//     *
//     * <p>삭제된 회차를 가리키는 지난 예약은 번호가 없다(null). 날짜는 그대로 보이므로 화면은 버틴다.
//     */
//    private Map<Long, Integer> sequencesOf(Set<Long> expoIds) {
//        Map<Long, Integer> sequences = new HashMap<>();
//        rounds.findByExpoIdInAndDeletedAtIsNull(expoIds).stream()
//                .collect(Collectors.groupingBy(Round::getExpoId))
//                .values()
//                .forEach(perExpo -> {
//                    List<Round> ordered = RoundSequence.ordered(perExpo);
//                    for (int i = 0; i < ordered.size(); i++) {
//                        sequences.put(ordered.get(i).getId(), i + 1);
//                    }
//                });
//        return sequences;
//    }

    @Transactional(readOnly = true)
    public MyReservationDetailResponse getMine(Long reservationId, AuthenticatedUser user) {
        requireMember(user);

        // 남의 예약도 404 다. 403 을 주면 그 번호의 예약이 존재한다는 사실이 드러나고,
        // reservationId 는 연속된 값이라 훑기 쉽다.
        Reservation reservation = reservations.findById(reservationId)
                .filter(r -> Objects.equals(r.getUserId(), user.userId()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "reservation not found: " + reservationId));

        Round round = rounds.findById(reservation.getRoundId()).orElse(null);
        PaymentTransaction payment = payments.findByRefIdIn(Set.of(reservationId))
                .stream().findFirst().orElse(null);
        Long expoId = reservation.getExpoId();
        Integer sequence = round == null || round.isDeleted() ? null : roundService.sequencesOf(Set.of(expoId)).get(round.getId());

        return MyReservationDetailResponse.of(reservation, round,
                expoClient.titles(Set.of(expoId)).get(expoId), sequence,
                refundStateOf(reservation, payment), ticketOf(reservationId));
    }

    /**
     * 티켓 조회는 부분 실패를 허용한다. 아직 발급되지 않았거나(404) 조회에 실패해도
     * 예약 정보는 그대로 내려간다 - 티켓 때문에 예약 화면 전체가 막히면 안 된다.
     */
    private ReservationTicketView ticketOf(Long reservationId) {
        TicketDetail ticket = ticketClient.findTicket(reservationId);
        return ticket == null ? null : ReservationTicketView.from(ticket);
    }

    private RefundState refundStateOf(Reservation reservation, PaymentTransaction payment) {
        return RefundState.of(reservation.getStatus(), payment, refundMaxAttempts);
    }

    private Map<Long, Round> roundsOf(Collection<Reservation> mine) {
        Set<Long> roundIds = mine.stream().map(Reservation::getRoundId).collect(Collectors.toSet());
        return rounds.findAllById(roundIds).stream()
                .collect(Collectors.toMap(Round::getId, Function.identity()));
    }

    private Map<Long, PaymentTransaction> paymentsOf(Collection<Reservation> mine) {
        Set<Long> reservationIds = mine.stream().map(Reservation::getId).collect(Collectors.toSet());
        return payments.findByRefIdIn(reservationIds).stream()
                .collect(Collectors.toMap(PaymentTransaction::getRefId, Function.identity()));
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
