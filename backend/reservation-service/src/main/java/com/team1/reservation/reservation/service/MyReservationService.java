package com.team1.reservation.reservation.service;

import com.team1.payment.PaymentTransaction;
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
import com.team1.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
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
    private final int refundMaxAttempts;

    public MyReservationService(ReservationRepository reservations,
                                RoundRepository rounds,
                                PaymentLookupRepository payments,
                                TicketClient ticketClient,
                                @Value("${scheduler.refund-retry.max-attempts}") int refundMaxAttempts) {
        this.reservations = reservations;
        this.rounds = rounds;
        this.payments = payments;
        this.ticketClient = ticketClient;
        this.refundMaxAttempts = refundMaxAttempts;
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

        return mine.stream()
                .map(reservation -> MyReservationResponse.of(
                        reservation,
                        roundsById.get(reservation.getRoundId()),
                        refundStateOf(reservation, paymentsByReservationId.get(reservation.getId()))))
                .toList();
    }

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

        return MyReservationDetailResponse.of(reservation, round,
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
