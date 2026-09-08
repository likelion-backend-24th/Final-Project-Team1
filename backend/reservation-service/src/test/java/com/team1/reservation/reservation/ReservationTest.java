package com.team1.reservation.reservation;

import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    private static final Instant NOW = Instant.parse("2026-09-08T04:00:00Z");

    private static Reservation create(int headcount, int amount) {
        return Reservation.create("R-4K7Q-W2M8", 1L, 10L, 100L,
                "홍길동", "01012345678", headcount, amount, NOW);
    }

    @Test
    @DisplayName("정상 입력이면 PENDING 으로 만들어지고 만료 시각은 생성 +10분이다")
    void createsPendingReservation() {
        Reservation reservation = create(3, 30000);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getCreatedAt()).isEqualTo(NOW);
        assertThat(reservation.getExpiresAt()).isEqualTo(NOW.plus(Reservation.PAYMENT_WINDOW));
        assertThat(reservation.getExpiresAt()).isEqualTo(Instant.parse("2026-09-08T04:10:00Z"));
        assertThat(reservation.getConfirmedAt()).isNull();
        assertThat(reservation.getCancelledAt()).isNull();
    }

    @Test
    @DisplayName("무료 회차라 결제 금액이 0이어도 만들어진다")
    void allowsZeroAmount() {
        assertThat(create(1, 0).getAmount()).isZero();
    }

    @Test
    @DisplayName("인원이 1 미만이면 거절한다")
    void rejectsHeadcountBelowOne() {
        assertThatThrownBy(() -> create(0, 10000))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("결제 금액이 음수면 거절한다")
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> create(1, -1))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("연락처에 하이픈이 남아 있으면 거절한다 - 정규화를 빠뜨린 호출 경로를 엔티티에서 막는다")
    void rejectsPhoneWithHyphen() {
        assertThatThrownBy(() -> Reservation.create("R-4K7Q-W2M8", 1L, 10L, 100L,
                "홍길동", "010-1234-5678", 1, 10000, NOW))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("예약자 이름이 비어 있으면 거절한다")
    void rejectsBlankContactName() {
        assertThatThrownBy(() -> Reservation.create("R-4K7Q-W2M8", 1L, 10L, 100L,
                "  ", "01012345678", 1, 10000, NOW))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("toString 에 예약자 이름과 연락처가 나타나지 않는다")
    void hidesContactInToString() {
        String printed = create(2, 20000).toString();

        assertThat(printed).doesNotContain("홍길동");
        assertThat(printed).doesNotContain("01012345678");
        assertThat(printed).contains("R-4K7Q-W2M8", "PENDING");
    }
}
