package com.team1.ticket.ticket.repository;

import com.team1.ticket.ticket.dto.CheckinSummaryItem;
import com.team1.ticket.ticket.entity.Ticket;
import com.team1.ticket.ticket.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // 예약당 티켓 1건이므로 단건 조회. 멱등 판단·무효화에 사용한다.
    Optional<Ticket> findByReservationId(Long reservationId);

    // 체크인: QR 로 스캔한 체크인 토큰으로 티켓을 찾는다.
    Optional<Ticket> findByCheckinToken(String checkinToken);

    // 체크인 현황 집계(#121). 회차별 체크인 완료 인원 = USED 티켓의 headcount 합.
    // 체크인이 0인 회차는 결과에 나타나지 않으며, 박람회-Service 가 병합 시 0 으로 채운다.
    @Query("select new com.team1.ticket.ticket.dto.CheckinSummaryItem(t.roundId, sum(t.headcount)) "
            + "from Ticket t where t.expoId = :expoId and t.status = :status group by t.roundId")
    List<CheckinSummaryItem> sumCheckedInByRound(@Param("expoId") Long expoId,
                                                 @Param("status") TicketStatus status);
}
