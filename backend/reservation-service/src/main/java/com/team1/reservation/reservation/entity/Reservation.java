package com.team1.reservation.reservation.entity;

import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;


/**
 * 예약 Aggregate Root.
 *
 * <p>상태 전이 규칙을 Service 가 아니라 이 클래스 안에 둔다. 예약은 결제·만료 스케줄러·사용자 취소
 * 세 경로에서 상태가 바뀌는데, 규칙이 Service 에 흩어지면 경로마다 조건이 어긋나기 쉽다.
 *
 * <p>허용 전이는 네 가지뿐이다.
 * <pre>
 *   PENDING   → CONFIRMED   결제 승인
 *   PENDING   → CANCELLED   결제 실패
 *   PENDING   → EXPIRED     결제 대기 시간 초과
 *   CONFIRMED → CANCELLED   사용자 취소
 * </pre>
 * 종료 상태(CANCELLED·EXPIRED)에서 나가는 전이는 없다.
 *
 * <p>시각은 전부 UTC {@link Instant} 이며 {@code now} 를 파라미터로 받는다.
 * Test 에서 시간을 고정할 수 있어야 하기 때문이고, {@code Round} 와 같은 방식이다.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    /** 결제 대기 시간. 이 시간이 지나면 #77 의 스케줄러가 EXPIRED 로 바꾸고 정원을 되돌린다. */
    public static final Duration PAYMENT_WINDOW = Duration.ofMinutes(10);

    /**
     * 연락처는 하이픈을 제거한 숫자만 받는다. 정규화는 Service 계층(#76)이 하지만,
     * 엔티티에서도 막아야 정규화를 빠뜨린 호출 경로가 생겨도 DB 에 표기 형식이 섞이지 않는다.
     */
    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d{9,15}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_no", nullable = false, length = 20)
    private String reservationNo;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    @Column(name = "expo_id", nullable = false)
    private Long expoId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;

    @Column(name = "headcount", nullable = false)
    private int headcount;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected Reservation() {
    }

    private Reservation(String reservationNo, Long roundId, Long expoId, Long userId,
                        String contactName, String contactPhone,
                        int headcount, int amount, Instant createdAt) {
        this.reservationNo = reservationNo;
        this.roundId = roundId;
        this.expoId = expoId;
        this.userId = userId;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.headcount = headcount;
        this.amount = amount;
        this.status = ReservationStatus.PENDING;
        this.createdAt = createdAt;
        this.expiresAt = createdAt.plus(PAYMENT_WINDOW);
    }

    public static Reservation create(String reservationNo, Long roundId, Long expoId, Long userId,
                                     String contactName, String contactPhone,
                                     int headcount, int amount, Instant now) {
        if (reservationNo == null || reservationNo.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "reservationNo must not be blank");
        }
        if (roundId == null || expoId == null || userId == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "roundId, expoId, userId must not be null");
        }
        if (contactName == null || contactName.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "contactName must not be blank");
        }
        if (contactPhone == null || !DIGITS_ONLY.matcher(contactPhone).matches()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "contactPhone must be digits only");
        }
        if (headcount < 1) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "headcount must be at least 1");
        }
        if (amount < 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "amount must not be negative");
        }
        return new Reservation(reservationNo, roundId, expoId, userId,
                contactName, contactPhone, headcount, amount, now);
    }

    /** 결제 승인. 결제 대기 시간이 이미 지난 예약은 승인하지 않는다. */
    public void confirm(Instant now) {
        requireStatus(ReservationStatus.PENDING, "confirm");
        if (!now.isBefore(expiresAt)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "payment window has already passed");
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    /** 결제 실패(PENDING) 또는 사용자 취소(CONFIRMED). 둘 다 CANCELLED 로 끝난다. */
    public void cancel(Instant now) {
        if (status != ReservationStatus.PENDING && status != ReservationStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "cannot cancel from " + status);
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = now;
    }

    /**
     * 결제 대기 만료. 만료 시각이 아직 지나지 않았으면 거절한다 —
     * 스케줄러가 조회 조건을 잘못 짜서 살아 있는 예약을 지우는 사고를 엔티티에서 한 번 더 막는다.
     *
     * <p>{@code cancelledAt} 은 채우지 않는다. EXPIRED 의 종료 시각은 {@code expiresAt} 이다.
     */
    public void expire(Instant now) {
        requireStatus(ReservationStatus.PENDING, "expire");
        if (now.isBefore(expiresAt)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "reservation has not expired yet");
        }
        this.status = ReservationStatus.EXPIRED;
    }

    private void requireStatus(ReservationStatus required, String action) {
        if (status != required) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "cannot " + action + " from " + status);
        }
    }

    public Long getId() {
        return id;
    }

    public String getReservationNo() {
        return reservationNo;
    }

    public Long getRoundId() {
        return roundId;
    }

    public Long getExpoId() {
        return expoId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public int getHeadcount() {
        return headcount;
    }

    public int getAmount() {
        return amount;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    /**
     * 예약자 이름·연락처를 제외한다. 예외 Log 나 Debug 출력에 개인정보가 남지 않게 하기 위해서이며,
     * Sprint 1 의 {@code LoginRequest}·{@code SignUpRequest} 와 같은 방식이다.
     */
    @Override
    public String toString() {
        return "Reservation{id=" + id
                + ", reservationNo=" + reservationNo
                + ", roundId=" + roundId
                + ", userId=" + userId
                + ", headcount=" + headcount
                + ", amount=" + amount
                + ", status=" + status
                + '}';
    }
}
