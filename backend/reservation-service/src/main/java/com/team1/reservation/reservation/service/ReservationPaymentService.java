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

/**
 * 결제 결과를 받아 예약 상태를 전이시킨다.
 *
 * <p>경계가 분명하다. <b>결제 자체의 상태는 이 Service 가 관리하지 않는다</b> —
 * PortOne 호출, 금액 검증, {@code payment_transactions} 의 상태 전이, 이중결제 방지는 전부
 * {@link PaymentService}(파트 B)의 몫이다. 여기서는 모듈이 돌려준 네 가지 결과를 받아
 * {@link Reservation} 만 전이시킨다.
 *
 * <p>웹훅 수신 엔드포인트도 {@link #applyPaymentResult} 를 그대로 재사용한다. 확정 경로가
 * 둘로 갈라지면 한쪽만 고치는 사고가 반드시 난다.
 */
@Service
public class ReservationPaymentService {

    private static final Logger log = LoggerFactory.getLogger(ReservationPaymentService.class);

    static final String ROLE_MEMBER = "USER";

    private final ReservationRepository reservations;
    private final RoundRepository rounds;
    private final PaymentService paymentService;
    private final Clock clock;

    public ReservationPaymentService(ReservationRepository reservations,
                                     RoundRepository rounds,
                                     PaymentService paymentService,
                                     Clock clock) {
        this.reservations = reservations;
        this.rounds = rounds;
        this.paymentService = paymentService;
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

    /**
     * 확정 로직 본체. 웹훅 엔드포인트가 서명검증을 거친 뒤 같은 메서드를 부른다.
     */
    @Transactional
    public Reservation applyPaymentResult(Reservation reservation) {
        switch (reservation.getStatus()) {
            case CONFIRMED -> {
                return reservation;
            }
            case CANCELLED, EXPIRED -> throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "reservation is already " + reservation.getStatus());
            case PENDING -> {
                // 아래에서 계속 처리한다
            }
        }

        PaymentApprovalResult result = paymentService.confirm(reservation.getId());

        return switch (result.outcome()) {
            case SUCCESS -> {
                reservation.confirm(clock.instant());
                // 티켓 발급 통지(#79)가 여기에 붙는다. 실패해도 확정을 되돌리지 않는 fail-open 이라
                // 이 Transaction 밖에서 트리거해야 한다.
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
