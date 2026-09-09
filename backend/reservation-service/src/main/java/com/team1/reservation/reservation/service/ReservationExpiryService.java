package com.team1.reservation.reservation.service;

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

/**
 * 결제되지 않은 채 결제창을 넘긴 예약을 EXPIRED 로 정리하고 정원을 돌려준다(#77). 유예시간은 늦은 통지를 기다리는
 * 시간이지 결제 시간을 늘려 주는 것이 아니다.
 */
@Service
public class ReservationExpiryService {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryService.class);

    private final ReservationRepository reservations;
    private final ReservationExpiryWriter writer;
    private final Clock clock;
    private final Duration grace;
    private final int batchSize;

    public ReservationExpiryService(ReservationRepository reservations,
                                    ReservationExpiryWriter writer,
                                    Clock clock,
                                    @Value("${reservation.expiry.grace}") Duration grace,
                                    @Value("${reservation.expiry.batch-size}") int batchSize) {
        this.reservations = reservations;
        this.writer = writer;
        this.clock = clock;
        this.grace = grace;
        this.batchSize = batchSize;
    }

    /** 만료 대상을 훑어 건별로 정리한다. 반환값은 실제로 전이시킨 건수다. */
    public int expireDue() {
        Instant cutoff = clock.instant().minus(grace);
        List<Reservation> candidates =
                reservations.findExpirable(cutoff, PageRequest.of(0, batchSize));

        int expired = 0;
        for (Reservation candidate : candidates) {
            // 한 건이 터져도 배치는 계속 간다. 남은 것은 다음 주기가 다시 집는다.
            try {
                if (writer.expire(candidate.getId(), candidate.getRoundId(), candidate.getHeadcount())) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.error("RESERVATION_EXPIRY_FAILED reservationId={} traceId={} reason={}",
                        candidate.getId(), TraceId.get(), e.toString());
            }
        }

        if (expired > 0) {
            log.info("expired {} of {} candidates cutoff={}", expired, candidates.size(), cutoff);
        }
        return expired;
    }
}
