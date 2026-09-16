package com.team1.reservation.round.entity;

import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;


@Entity
@Table(name = "rounds")
@Getter
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
        validate(startsAt, endsAt, capacity, fee, now);
        return new Round(expoId, startsAt, endsAt, capacity, fee, now);
    }

    /**
     * 등록과 수정이 공유하는 불변식. 수정은 조건부 UPDATE 로 나가 엔티티를 거치지 않으므로
     * 검증을 여기 static 으로 둔다 - 두 경로가 다른 규칙을 쓰는 일이 없어야 한다.
     */
    public static void validate(Instant startsAt, Instant endsAt, int capacity, int fee, Instant now) {
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
    }

    /** 잔여 정원. 조건부 UPDATE 가 갱신한 reserved_count 를 그대로 반영한다. */
    public int remaining() {
        return capacity - reservedCount;
    }
}
