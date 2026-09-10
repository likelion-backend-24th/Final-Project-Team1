package com.team1.reservation.reservation;

import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.support.MyReservationFixture;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * #82 소유권. 타인의 예약은 403 이 아니라 404 다 - 403 을 주면 그 번호의 예약이
 * 존재한다는 사실이 드러나고, reservationId 는 연속된 값이라 훑기 쉽다.
 */
class ReservationOwnershipTest extends MyReservationFixture {

    @BeforeEach
    void setUp() {
        initMocks();
    }

    @Test
    @DisplayName("남의 예약은 404 이고 티켓을 조회하지도 않는다")
    void othersReservationIsNotFound() {
        when(reservations.findById(RESERVATION_ID))
                .thenReturn(Optional.of(reservation(RESERVATION_ID, 999L, ReservationStatus.CONFIRMED)));

        assertThatThrownBy(() -> service.getMine(RESERVATION_ID, MEMBER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));

        verifyNoInteractions(ticketClient);
    }

    @Test
    @DisplayName("없는 예약도 같은 404 - 응답으로 두 경우를 구분할 수 없어야 한다")
    void missingReservationIsSameNotFound() {
        when(reservations.findById(RESERVATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMine(RESERVATION_ID, MEMBER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("목록에는 본인 예약만 조회한다")
    void listsOnlyOwnReservations() {
        givenMine(ReservationStatus.CONFIRMED);

        service.listMine(MEMBER);

        verify(reservations).findByUserIdOrderByCreatedAtDesc(USER_ID);
        verify(reservations, never()).findAll();
    }

    @Test
    @DisplayName("USER 가 아닌 Role 은 403, 미인증은 401 - 예약을 조회하지도 않는다")
    void rejectsNonMember() {
        assertThatThrownBy(() -> service.listMine(new AuthenticatedUser(1L, "ORGANIZER")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.getMine(RESERVATION_ID, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));

        verify(reservations, never()).findById(anyLong());
    }

    @Test
    @DisplayName("예약이 없으면 빈 목록이다 - 오류가 아니다")
    void emptyListIsNotAnError() {
        when(reservations.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());

        assertThat(service.listMine(MEMBER)).isEmpty();
        verifyNoInteractions(ticketClient);
    }
}
