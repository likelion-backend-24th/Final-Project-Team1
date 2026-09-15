package com.team1.ticket.ticket.repository;

import com.team1.ticket.ticket.entity.CheckinLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface CheckinLogRepository extends JpaRepository<CheckinLog, Long> {

    // 조회 API 는 아직 없다(S7-5 범위 밖). Test 와 향후 화면이 쓴다.
    List<CheckinLog> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
