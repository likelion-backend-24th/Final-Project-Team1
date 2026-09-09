package com.team1.reservation.reservation.service;

import com.team1.payment.PaymentApprovalResult;
import com.team1.payment.PaymentService;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.common.TraceId;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;


//결제 결과를 받아 예약 상태를 전이시킨다.

@Service
public class ReservationPaymentService {

    private static final Logger log = LoggerFactory.getLogger(ReservationPaymentService.class);

    static final String ROLE_MEMBER = "USER";

    private final ReservationRepository reservations;
    private final RoundRepository rounds;
    private final PaymentService paymentService;
    private final TicketIssueNotifier ticketIssueNotifier;
    private final Clock clock;

    public ReservationPaymentService(ReservationRepository reservations,
                                     RoundRepository rounds,
                                     PaymentService paymentService,
                                     TicketIssueNotifier ticketIssueNotifier,
                                     Clock clock) {
        this.reservations = reservations;
        this.rounds = rounds;
        this.paymentService = paymentService;
        this.ticketIssueNotifier = ticketIssueNotifier;
        this.clock = clock;
    }

    /** 외부 API 진입점. 인증·소유권을 확인한 뒤 공통 확정 로직으로 넘긴다. */
    @Transactional
    public Reservation confirm(Long reservationId, AuthenticatedUser user) {
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "authentication required");
        }
        if (!ROLE_MEMBER.equals(user.role())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "USER role required");
        }

        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "reservation not found: " + reservationId));

        // 이 Endpoint 는 조회가 아니라 자기 예약에 대한 작업이므로, 계약대로 403 이다.
        if (!Objects.equals(reservation.getUserId(), user.userId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "not the owner of reservation " + reservationId);
        }

        return applyPaymentResult(reservation);
    }

    /** 모듈에 결제를 조회해 그 결과를 예약에 반영한다. */
    @Transactional
    public Reservation applyPaymentResult(Reservation reservation) {
        if (alreadyDecided(reservation)) {
            return reservation;
        }
        return applyOutcome(reservation, paymentService.confirm(reservation.getId()));
    }

    /** 웹훅이 이미 받아 둔 결과를 반영한다. PG 를 다시 조회하지 않는다. */
    @Transactional
    public Reservation applyOutcome(Reservation reservation, PaymentApprovalResult result) {
        if (alreadyDecided(reservation)) {
            return reservation;
        }

        return switch (result.outcome()) {
            case SUCCESS -> {
                reservation.confirm(clock.instant());
                // 실제 전이가 일어난 경로에서만 통지한다. 멱등 재호출은 위에서 이미 빠져나갔다.
                ticketIssueNotifier.notifyIssued(reservation);
                yield reservation;
            }

            case FAILED_CONFIRMED -> cancelFailed(reservation, result);

            // 금액이 다르면 확정도 취소도 하지 않는다. 자동 취소하면 참가비 설정 실수 하나로
            // 정상 결제가 사라지므로, 사람이 확인할 여지를 남긴다.
            case AMOUNT_MISMATCH -> {
                log.warn("payment amount mismatch reservationId={} expected={} traceId={}",
                        reservation.getId(), reservation.getAmount(), TraceId.get());
                throw new ApiException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                        "verified amount does not match the reservation amount");
            }

            // 모름. 확정하면 돈 안 낸 사람에게 자리를 주고, 취소하면 돈 낸 사람의 자리를 뺏는다.
            // 아무것도 하지 않는 것이 유일하게 안전한 선택이다(fail-closed).
            case UNKNOWN -> {
                log.warn("payment result unknown reservationId={} traceId={}",
                        reservation.getId(), TraceId.get());
                throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "payment result unknown");
            }

            case ALREADY_PROCESSED, IGNORED -> throw new IllegalStateException(
                    "confirm() 은 웹훅 전용 결과를 반환하지 않는다: " + result.outcome());
        };
    }

    /** CONFIRMED 면 true(멱등), CANCELLED·EXPIRED 면 409, PENDING 이면 false. */
    private boolean alreadyDecided(Reservation reservation) {
        return switch (reservation.getStatus()) {
            case CONFIRMED -> true;
            case CANCELLED, EXPIRED -> throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "reservation is already " + reservation.getStatus());
            case PENDING -> false;
        };
    }

    private Reservation cancelFailed(Reservation reservation, PaymentApprovalResult result) {
        log.info("payment failed reservationId={} reason={} traceId={}",
                reservation.getId(), result.failureReason(), TraceId.get());

        // 순서가 중요하다. cancel() 이 먼저다 - PENDING 이 아니면 여기서 예외가 나고
        // 정원 반환은 실행되지 않는다. 그래서 반환은 상태 전이당 정확히 1회다.
        reservation.cancel(clock.instant());
        rounds.release(reservation.getRoundId(), reservation.getHeadcount());

        return reservation;
    }
}
