package com.team1.reservation.round.entity;

import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;


@Entity
@Table(name = "rounds")
public class Round {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expo_id", nullable = false)
    private Long expoId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    /**
     * PENDING·CONFIRMED 예약 인원의 합. 조건부 UPDATE(RoundRepository#reserve, #release)로만 바뀐다.
     * setter 를 두지 않는 이유는, 여기서 값을 바꾸면 정원 초과 판정이 DB 밖으로 새기 때문이다.
     */
    @Column(name = "reserved_count", nullable = false)
    private int reservedCount;

    @Column(name = "fee", nullable = false)
    private int fee;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Round() {
    }

    private Round(Long expoId, Instant startsAt, Instant endsAt, int capacity, int fee, Instant createdAt) {
        this.expoId = expoId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.capacity = capacity;
        this.fee = fee;
        this.createdAt = createdAt;
    }


    public static Round create(Long expoId, Instant startsAt, Instant endsAt, int capacity, int fee, Instant now) {
        if (capacity < 1) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "capacity must be at least 1");
        }
        if (fee < 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "fee must not be negative");
        }
        if (!startsAt.isAfter(now)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "startsAt must be in the future");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "endsAt must be after startsAt");
        }
        return new Round(expoId, startsAt, endsAt, capacity, fee, now);
    }

    /** 잔여 정원. 조건부 UPDATE 가 갱신한 reserved_count 를 그대로 반영한다. */
    public int remaining() {
        return capacity - reservedCount;
    }

    public Long getId() {
        return id;
    }

    public Long getExpoId() {
        return expoId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getReservedCount() {
        return reservedCount;
    }

    public int getFee() {
        return fee;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
