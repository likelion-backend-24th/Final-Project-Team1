package com.team1.reservation.reservation.service;

import com.team1.payment.PaymentApprovalResult;
import com.team1.payment.PaymentService;
import com.team1.payment.PaymentStatus;
import com.team1.payment.PaymentTransaction;
import com.team1.payment.PaymentTransactionRepository;
import com.team1.reservation.common.TraceId;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 결제되지 않은 예약을 정리하고 정원을 돌려준다(#77).
 *
 * <p>이 배치는 웹훅의 백업이다. 결제 결과의 주경로는 PortOne 웹훅이고, 웹훅마저 놓쳤을 때
 * 여기서 잡는다. 둘 다 같은 멱등 경로({@link ReservationPaymentService#applyOutcome})를
 * 호출하므로 동시에 돌아도 같은 결과로 수렴한다.
 *
 * <p>핵심은 <b>만료 전에 결제 여부를 한 번 더 확인한다</b>는 것이다. 시간이 됐다고 무조건
 * 만료시키면, 마지막 순간에 결제하고 웹훅이 유실된 사용자의 돈을 받고 자리를 뺏는다.
 */
@Service
public class ReservationExpiryService {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryService.class);

    private final ReservationRepository reservations;
    private final PaymentTransactionRepository payments;
    private final PaymentService paymentService;
    private final ReservationExpiryWriter writer;
    private final Clock clock;
    private final Duration grace;
    private final int batchSize;
    private final int pgBatchSize;

    public ReservationExpiryService(ReservationRepository reservations,
                                    PaymentTransactionRepository payments,
                                    PaymentService paymentService,
                                    ReservationExpiryWriter writer,
                                    Clock clock,
                                    @Value("${scheduler.reservation-expiry.grace}") Duration grace,
                                    @Value("${scheduler.reservation-expiry.batch-size}") int batchSize,
                                    @Value("${scheduler.reservation-expiry.pg-batch-size}") int pgBatchSize) {
        this.reservations = reservations;
        this.payments = payments;
        this.paymentService = paymentService;
        this.writer = writer;
        this.clock = clock;
        this.grace = grace;
        this.batchSize = batchSize;
        this.pgBatchSize = pgBatchSize;
    }

    /** 만료 대상을 훑어 건별로 정리한다. 반환값은 실제로 전이시킨 건수다. */
    public int expireDue() {
        Instant now = clock.instant();
        List<Reservation> candidates =
                reservations.findExpirable(now, PageRequest.of(0, batchSize));

        int transitioned = 0;
        int pgCalls = 0;

        for (Reservation candidate : candidates) {
            // 한 건이 터져도 배치는 계속 간다. 남은 것은 다음 주기가 다시 집는다.
            try {
                Optional<PaymentTransaction> payment = payments.findByRefId(candidate.getId());

                if (!needsPgCheck(payment)) {
                    if (writer.expire(candidate.getId(), candidate.getRoundId(), candidate.getHeadcount())) {
                        transitioned++;
                    }
                    continue;
                }

                // PG 조회는 외부 호출이라 별도 상한을 둔다. 배치가 여기에 밀리면
                // 조회가 필요 없는 갈래 1 까지 다음 주기로 밀린다.
                if (pgCalls >= pgBatchSize) {
                    continue;
                }
                pgCalls++;
                if (settleWithPg(candidate, payment.orElse(null), now)) {
                    transitioned++;
                }

            } catch (RuntimeException e) {
                log.error("RESERVATION_EXPIRY_FAILED reservationId={} traceId={} reason={}",
                        candidate.getId(), TraceId.get(), e.toString());
            }
        }

        if (transitioned > 0) {
            log.info("expiry cycle: candidates={} transitioned={} pgCalls={} now={}",
                    candidates.size(), transitioned, pgCalls, now);
        }
        return transitioned;
    }

    /**
     * 갈래 판정. 결제로 확정될 여지가 남아 있을 때만 PG 를 조회한다.
     *
     * <p>이슈 본문은 {@code pg_transaction_id} 유무로 가르라고 했지만 이 코드베이스에서는
     * 그 컬럼이 <b>승인에 성공한 뒤에야</b> 채워진다. 그대로 따르면 "결제했는데 웹훅이 유실된"
     * 바로 그 예약이 갈래 1 로 떨어져 즉시 만료된다 — 이 이슈가 막으려는 상황 그 자체다.
     * 그래서 로컬 결제 상태로 가른다.
     */
    private boolean needsPgCheck(Optional<PaymentTransaction> payment) {
        return payment
                .map(p -> p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.PAID)
                .orElse(false);
    }

    private boolean settleWithPg(Reservation reservation, PaymentTransaction payment, Instant now) {
        PaymentApprovalResult result = paymentService.confirm(reservation.getId());

        return switch (result.outcome()) {
            // 확정·취소 모두 #78 의 전이 경로를 그대로 탄다(티켓 통지 포함).
            case SUCCESS, FAILED_CONFIRMED -> writer.settle(reservation.getId(), result);

            // 모르면 만료를 보류한다. 확정하면 안 낸 사람에게 자리를 주고,
            // 만료시키면 낸 사람의 자리를 뺏는다.
            case UNKNOWN, AMOUNT_MISMATCH -> expireIfGraceElapsed(reservation, payment, now, result);

            case ALREADY_PROCESSED, IGNORED -> false;
        };
    }

    /**
     * 유예 상한. 무한 보류는 PG 무응답이 이어질 때 정원을 영구히 묶는다. 결제대기 창과 같은
     * 길이를 한 번 더 주고 그래도 모르면 만료시킨다. 컬럼을 추가하지 않고 expires_at 으로만 판정한다.
     */
    private boolean expireIfGraceElapsed(Reservation reservation, PaymentTransaction payment,
                                         Instant now, PaymentApprovalResult result) {
        if (!now.isAfter(reservation.getExpiresAt().plus(grace))) {
            return false;
        }
        if (!writer.expire(reservation.getId(), reservation.getRoundId(), reservation.getHeadcount())) {
            return false;
        }
        // CS 문의가 들어오면 이 로그로 찾는다.
        log.warn("EXPIRED_AFTER_GRACE reservationId={} pgTransactionId={} paymentId={} "
                        + "expiresAt={} now={} lastOutcome={} traceId={}",
                reservation.getId(),
                payment == null ? null : payment.getPgTransactionId(),
                payment == null ? null : payment.getPaymentId(),
                reservation.getExpiresAt(), now, result.outcome(), TraceId.get());
        return true;
    }
}
