package com.team1.reservation.reservation.service;

import com.team1.payment.PaymentService;
import com.team1.payment.PaymentStatus;
import com.team1.payment.PaymentTransaction;
import com.team1.payment.PaymentTransactionRepository;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.common.TraceId;
import com.team1.reservation.reservation.dto.CancelReservationResponse;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/*
 사용자 예약 취소.
 박람회에는 날짜 컬럼이 없고 개최 시각은
 전부 회차에 있으며, 예약자에게 "박람회 날짜" 는 곧 자기 회차 날짜다.
 */
@Service
public class ReservationCancelService {

    private static final Logger log = LoggerFactory.getLogger(ReservationCancelService.class);

    static final String ROLE_MEMBER = "USER";

    /* 전액 환불 창. 회차 시작 이 시간 전까지 취소해야 돈이 돌아간다. */
    static final Duration REFUND_WINDOW = Duration.ofDays(1);

    private final ReservationRepository reservations;
    private final RoundRepository rounds;
    private final PaymentTransactionRepository payments;
    private final PaymentService paymentService;
    private final TicketIssueNotifier ticketNotifier;
    private final Clock clock;

    public ReservationCancelService(ReservationRepository reservations,
                                    RoundRepository rounds,
                                    PaymentTransactionRepository payments,
                                    PaymentService paymentService,
                                    TicketIssueNotifier ticketNotifier,
                                    Clock clock) {
        this.reservations = reservations;
        this.rounds = rounds;
        this.payments = payments;
        this.paymentService = paymentService;
        this.ticketNotifier = ticketNotifier;
        this.clock = clock;
    }

    @Transactional
    public CancelReservationResponse cancel(Long reservationId, AuthenticatedUser user) {
        requireMember(user);

        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "reservation not found: " + reservationId));

        // 조회가 아니라 자기 예약에 대한 작업이므로 계약대로 403 이다.
        if (!Objects.equals(reservation.getUserId(), user.userId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "not the owner of reservation " + reservationId);
        }

        Round round = rounds.findById(reservation.getRoundId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "round not found: " + reservation.getRoundId()));

        Instant now = clock.instant();
        if (!now.isBefore(round.getStartsAt())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "cancellation closed at round start");
        }

        // 순서가 중요하다. 전이가 먼저다 - 0 행이면 만료 배치나 웹훅이 먼저 끝낸 것이고,
        // 그때 정원을 반환하면 두 번 돌려주게 된다.
        if (reservations.cancelIfActive(reservationId, now) == 0) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "reservation is no longer cancellable");
        }
        rounds.release(reservation.getRoundId(), reservation.getHeadcount());

        int refunded = refundIfEligible(reservation, round, now);

        // 무효화는 fail-open 이지만 큐에 적재되므로 실패해도 회수된다.
        ticketNotifier.notifyRevoked(reservation);

        return new CancelReservationResponse(reservationId, "CANCELLED", now, refunded > 0, refunded);
    }

    /** 환불한 금액. 창을 지났거나 결제가 없으면 0 이다. */
    private int refundIfEligible(Reservation reservation, Round round, Instant now) {
        if (reservation.getAmount() == 0) {
            return 0;
        }
        if (now.isAfter(round.getStartsAt().minus(REFUND_WINDOW))) {
            log.info("cancelled without refund reservationId={} startsAt={} now={} traceId={}",
                    reservation.getId(), round.getStartsAt(), now, TraceId.get());
            return 0;
        }

        Optional<PaymentTransaction> payment = payments.findByRefId(reservation.getId());
        if (payment.isEmpty() || payment.get().getStatus() != PaymentStatus.PAID) {
            return 0;
        }

        // 모듈이 PG 무응답을 REFUND_FAILED 로 기록하고 예외를 던지지 않는다. 취소 자체는
        // 되돌리지 않는 것이 맞다 - 사용자에게 자리를 다시 떠안기느니 환불을 재시도하는 편이 낫다.
        paymentService.cancel(reservation.getId(), "user cancellation");
        return reservation.getAmount();
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
