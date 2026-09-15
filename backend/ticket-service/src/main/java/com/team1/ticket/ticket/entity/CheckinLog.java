package com.team1.ticket.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;


// 체크인 이력(S7-5). append-only - 한 번 쌓은 행은 고치지 않는다.
@Entity
@Table(name = "checkin_logs")
public class CheckinLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private Long ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 20)
    private CheckinAction action;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", updatable = false, length = 20)
    private CheckinMethod method;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CheckinLog() {
    }

    private CheckinLog(Long ticketId, CheckinAction action, Long actorUserId,
                       CheckinMethod method, Instant createdAt) {
        this.ticketId = ticketId;
        this.action = action;
        this.actorUserId = actorUserId;
        this.method = method;
        this.createdAt = createdAt;
    }

    public static CheckinLog of(Long ticketId, CheckinAction action, Long actorUserId,
                                CheckinMethod method, Instant now) {
        return new CheckinLog(ticketId, action, actorUserId, method, now);
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public CheckinAction getAction() {
        return action;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public CheckinMethod getMethod() {
        return method;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
