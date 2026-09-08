package com.team1.ticket.ticket.entity;

import com.team1.ticket.common.ApiException;
import com.team1.ticket.common.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;


@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 다른 Service DB 값에 대한 논리 참조(FK 아님). reservation=reservation.reservations.id 등.
    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "expo_id", nullable = false)
    private Long expoId;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    // 티켓 소유 회원(identity.users.id 논리 참조). "내 티켓 조회"에 사용.
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TicketStatus status;

    // 체크인/QR 토큰. 현재는 불투명 토큰(서버 조회 방식). 서명 토큰 전환은 #74 에서 검토.
    @Column(name = "checkin_token", nullable = false, length = 64, unique = true)
    private String checkinToken;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected Ticket() {
    }

    private Ticket(Long reservationId, Long expoId, Long roundId, Long userId,
                   String checkinToken, Instant issuedAt) {
        this.reservationId = reservationId;
        this.expoId = expoId;
        this.roundId = roundId;
        this.userId = userId;
        this.status = TicketStatus.ISSUED;
        this.checkinToken = checkinToken;
        this.issuedAt = issuedAt;
    }

    public static Ticket issue(Long reservationId, Long expoId, Long roundId, Long userId,
                               String checkinToken, Instant now) {
        if (reservationId == null || expoId == null || roundId == null || userId == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "reservationId, expoId, roundId, userId are required");
        }
        return new Ticket(reservationId, expoId, roundId, userId, checkinToken, now);
    }

    // 예약 취소에 따른 무효화. 이미 사용(USED)된 티켓은 취소하지 않는다.
    public void cancel() {
        if (this.status == TicketStatus.USED) {
            throw new ApiException(ErrorCode.CONFLICT, "used ticket cannot be cancelled");
        }
        this.status = TicketStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public Long getExpoId() {
        return expoId;
    }

    public Long getRoundId() {
        return roundId;
    }

    public Long getUserId() {
        return userId;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public String getCheckinToken() {
        return checkinToken;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
