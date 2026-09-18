package com.team1.ticket.ticket.repository;

import com.team1.ticket.ticket.dto.CheckinLogStat;
import com.team1.ticket.ticket.entity.CheckinAction;
import com.team1.ticket.ticket.entity.CheckinLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface CheckinLogRepository extends JpaRepository<CheckinLog, Long> {

    List<CheckinLog> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    /**
     * 체크인 결과 요약(#259)이 쓰는 원자료. 이력에는 박람회가 없어 티켓으로 이어 붙인다.
     *
     * <p>FK 가 없어 연관 매핑도 없으므로 두 엔티티를 나란히 두고 id 로 잇는다.
     * 되돌린 이력(CANCEL)도 한 행으로 남아 있어 행위를 조건으로 걸러야 한다.
     */
    @Query("select l from CheckinLog l, Ticket t "
            + "where l.ticketId = t.id and t.expoId = :expoId and l.action = :action "
            + "order by l.createdAt asc")
    List<CheckinLog> findByExpoAndAction(@Param("expoId") Long expoId,
                                         @Param("action") CheckinAction action);

    /** 처리 방법별 건수. 화면이 방법을 안 보내면 method 가 null 이라 UNKNOWN 으로 모은다. */
    @Query("select new com.team1.ticket.ticket.dto.CheckinLogStat("
            + "  coalesce(str(l.method), 'UNKNOWN'), count(l)) "
            + "from CheckinLog l, Ticket t "
            + "where l.ticketId = t.id and t.expoId = :expoId and l.action = :action "
            + "group by l.method")
    List<CheckinLogStat> countByMethod(@Param("expoId") Long expoId,
                                       @Param("action") CheckinAction action);

    /** 되돌리기 건수. 오입력이 잦았는지 보는 값이다. */
    @Query("select count(l) from CheckinLog l, Ticket t "
            + "where l.ticketId = t.id and t.expoId = :expoId and l.action = :action")
    long countByAction(@Param("expoId") Long expoId, @Param("action") CheckinAction action);
}
