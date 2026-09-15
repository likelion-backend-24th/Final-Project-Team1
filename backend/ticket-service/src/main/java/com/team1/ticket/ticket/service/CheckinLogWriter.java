package com.team1.ticket.ticket.service;

import com.team1.ticket.ticket.entity.CheckinAction;
import com.team1.ticket.ticket.entity.CheckinLog;
import com.team1.ticket.ticket.entity.CheckinMethod;
import com.team1.ticket.ticket.repository.CheckinLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;


/**
 * 이력 기록(S7-5). 실패해도 체크인을 막지 않는다 - 예외를 삼키는 쪽은 호출부다.
 * <p><b>별도 Bean 인 이유.</b> 같은 Bean 안에서 부르면 프록시를 타지 않아 {@code REQUIRES_NEW} 가
 * 통째로 무시된다. 그래서 이 클래스에는 "조용히 기록" 같은 편의 메서드를 두지 않는다.
 * <p><b>{@code REQUIRES_NEW} 인 이유.</b> 체크인과 같은 트랜잭션에서 INSERT 가 실패하면
 * rollback-only 가 박혀, 호출부가 예외를 삼켜도 커밋 시점에 터진다. 이력 때문에 체크인이
 * 사라지는 일은 없어야 한다.
 */
@Service
public class CheckinLogWriter {

    private final CheckinLogRepository logs;

    public CheckinLogWriter(CheckinLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long ticketId, CheckinAction action, Long actorUserId,
                       CheckinMethod method, Instant now) {
        logs.save(CheckinLog.of(ticketId, action, actorUserId, method, now));
    }
}
