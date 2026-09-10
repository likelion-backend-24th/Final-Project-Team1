package com.team1.ticket.ticket;

import com.team1.ticket.support.IntegrationTestSupport;
import com.team1.ticket.ticket.dto.CheckinSummaryItem;
import com.team1.ticket.ticket.entity.Ticket;
import com.team1.ticket.ticket.repository.TicketRepository;
import com.team1.ticket.ticket.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// #121 체크인 집계 @Query 를 실제 MySQL 로 검증. (Mock 단위테스트로는 JPQL 이 검증되지 않는다.)
class CheckinSummaryIntegrationTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-20T05:00:00Z");
    private static final long EXPO_ID = 10L;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketService ticketService;

    @BeforeEach
    void clean() {
        ticketRepository.deleteAll();
    }

    private Ticket used(long reservationId, long roundId, int headcount, String token) {
        Ticket t = Ticket.issue(reservationId, EXPO_ID, roundId, 77L, headcount, token, NOW);
        t.checkIn(NOW);
        return t;
    }

    @Test
    @DisplayName("집계: 회차별 USED 티켓의 headcount 합만 반환하고, ISSUED·CANCELLED·타 박람회는 제외한다")
    void aggregatesUsedHeadcountPerRound() {
        // round 45: USED 3명 + USED 2명 = 5명
        ticketRepository.save(used(1L, 45L, 3, "u45a"));
        ticketRepository.save(used(2L, 45L, 2, "u45b"));
        // round 46: USED 1명
        ticketRepository.save(used(3L, 46L, 1, "u46"));
        // round 45: ISSUED(미체크인) → 집계 제외
        ticketRepository.save(Ticket.issue(4L, EXPO_ID, 45L, 77L, 9, "issued45", NOW));
        // round 47: CANCELLED → 집계 제외 (회차 자체가 목록에 안 나와야 함)
        Ticket cancelled = Ticket.issue(5L, EXPO_ID, 47L, 77L, 4, "cx47", NOW);
        cancelled.cancel();
        ticketRepository.save(cancelled);
        // 다른 박람회(99)의 USED → expoId 필터로 제외
        Ticket otherExpo = Ticket.issue(6L, 99L, 45L, 77L, 7, "other", NOW);
        otherExpo.checkIn(NOW);
        ticketRepository.save(otherExpo);

        List<CheckinSummaryItem> summary = ticketService.getCheckinSummary(EXPO_ID);

        assertThat(summary).containsExactlyInAnyOrder(
                new CheckinSummaryItem(45L, 5),   // 3 + 2, ISSUED 9 는 미포함
                new CheckinSummaryItem(46L, 1));   // 47(취소)·99(타 박람회)는 없음
    }

    @Test
    @DisplayName("집계: 체크인이 하나도 없으면 빈 목록을 반환한다")
    void returnsEmptyWhenNoCheckin() {
        ticketRepository.save(Ticket.issue(1L, EXPO_ID, 45L, 77L, 2, "issued", NOW));

        assertThat(ticketService.getCheckinSummary(EXPO_ID)).isEmpty();
    }
}
