package com.team1.reservation.reservation.service;

import com.team1.payment.PgClient;
import com.team1.payment.PgCommunicationException;
import com.team1.payment.PgInquiryResult;
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
 */
@Service
public class ReservationPaymentService {

    private static final Logger log = LoggerFactory.getLogger(ReservationPaymentService.class);

    static final String ROLE_MEMBER = "USER";

    private final ReservationRepository reservations;
    private final RoundRepository rounds;
    private final PgClient pgClient;
    private final Clock clock;

    public ReservationPaymentService(ReservationRepository reservations,
                                     RoundRepository rounds,
                                     PgClient pgClient,
                                     Clock clock) {
        this.reservations = reservations;
        this.rounds = rounds;
        this.pgClient = pgClient;
        this.clock = clock;
    }

    /** 외부 API 진입점. 인증·소유권을 확인한 뒤 공통 확정 로직으로 넘긴다. */
    @Transactional
    public Reservation confirm(Long reservationId, AuthenticatedUser user, String paymentId) {
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

        return applyPaymentResult(reservation, paymentId);
    }

    /**
     * 확정 로직 본체. 웹훅 엔드포인트가 인증을 거친 뒤 같은 메서드를 부른다.
     */
    @Transactional
    public Reservation applyPaymentResult(Reservation reservation, String paymentId) {
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

        PgInquiryResult result = inquire(paymentId, reservation.getId());

        return switch (result.status()) {
            case PAID -> confirmPaid(reservation, result);
            case FAILED -> cancelFailed(reservation, result);

            // "거래 없음" 은 실패가 아니라 모름이다. 결제창은 통과했는데 PG 쪽 반영이 아직
            // 안 됐을 수 있어서, 여기서 취소해버리면 실제로 결제된 건을 날리게 된다.
            case NOT_FOUND -> throw unavailable(paymentId, reservation.getId(), "not found at PG yet");
        };
    }

    private PgInquiryResult inquire(String paymentId, Long reservationId) {
        try {
            return pgClient.inquire(paymentId);
        } catch (PgCommunicationException e) {
            log.warn("payment inquiry failed reservationId={} traceId={}", reservationId, TraceId.get(), e);
            throw unavailable(paymentId, reservationId, "payment gateway unavailable");
        }
    }

    /**
     * 모름. 예약 상태도 정원도 건드리지 않는다(fail-closed). 재시도로 풀리거나,
     * 풀리지 않으면 10분 만료 스케줄러(#77)가 PG 를 다시 확인하고 정리한다.
     */
    private ApiException unavailable(String paymentId, Long reservationId, String reason) {
        log.warn("payment result unknown reservationId={} paymentId={} reason={} traceId={}",
                reservationId, paymentId, reason, TraceId.get());
        return new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "payment result unknown: " + reason);
    }

    private Reservation confirmPaid(Reservation reservation, PgInquiryResult result) {
        // 금액은 PG 응답과 DB 를 대조한다. 금액이 다르면 확정하지 않고 상태도 그대로 둔다 -
        // 자동으로 취소해버리면 단순 설정 오류로 정상 결제가 사라진다. 사람이 볼 문제다.
        if (result.amount() == null || result.amount() != reservation.getAmount()) {
            log.warn("payment amount mismatch reservationId={} expected={} actual={} traceId={}",
                    reservation.getId(), reservation.getAmount(), result.amount(), TraceId.get());
            throw new ApiException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "verified amount does not match the reservation amount");
        }

        reservation.confirm(clock.instant());

        // 티켓 발급 통지(#79)가 여기에 붙는다. 실패해도 확정을 되돌리지 않는 fail-open 이라
        // 이 Transaction 밖에서 트리거해야 한다.
        return reservation;
    }

    private Reservation cancelFailed(Reservation reservation, PgInquiryResult result) {
        log.info("payment failed reservationId={} code={} reason={} traceId={}",
                reservation.getId(), result.responseCode(), result.failureReason(), TraceId.get());

        // 순서가 중요하다. cancel() 이 먼저다 - PENDING 이 아니면 여기서 예외가 나고
        // 정원 반환은 실행되지 않는다. 그래서 반환은 상태 전이당 정확히 1회다.
        reservation.cancel(clock.instant());
        rounds.release(reservation.getRoundId(), reservation.getHeadcount());

        return reservation;
    }
}
