package com.team1.reservation.reservation;

import com.team1.reservation.reservation.dto.AttendeeResponse;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.service.ReservationQueryService;
import com.team1.reservation.reservation.support.QueryTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttendeeQueryTest extends QueryTestFixture {

    @Autowired
    private ReservationQueryService service;

    @Test
    @DisplayName("roundId 가 없으면 박람회 전체 명단이다 - 엑셀 다운로드가 이 경로를 쓴다")
    void returnsWholeExpoWithoutRoundId() {
        Long roundA = givenRound(EXPO_ID, 100);
        Long roundB = givenRound(EXPO_ID, 100);
        givenReservation(EXPO_ID, roundA, 1L, 1, ReservationStatus.CONFIRMED);
        givenReservation(EXPO_ID, roundB, 2L, 2, ReservationStatus.CONFIRMED);

        assertThat(service.attendees(EXPO_ID, null)).hasSize(2);
    }

    @Test
    @DisplayName("roundId 를 주면 그 회차만 반환한다")
    void filtersByRound() {
        Long roundA = givenRound(EXPO_ID, 100);
        Long roundB = givenRound(EXPO_ID, 100);
        givenReservation(EXPO_ID, roundA, 1L, 1, ReservationStatus.CONFIRMED);
        givenReservation(EXPO_ID, roundB, 2L, 2, ReservationStatus.CONFIRMED);

        List<AttendeeResponse> attendees = service.attendees(EXPO_ID, roundA);

        assertThat(attendees).hasSize(1);
        assertThat(attendees.get(0).roundId()).isEqualTo(roundA);
    }

    @Test
    @DisplayName("EXPIRED 는 명단에서 뺀다 - 입장도 연락도 할 일이 없는데 개인정보만 늘어난다")
    void excludesExpired() {
        Long roundId = givenRound(EXPO_ID, 100);
        givenReservation(EXPO_ID, roundId, 1L, 1, ReservationStatus.CONFIRMED);
        givenReservation(EXPO_ID, roundId, 2L, 1, ReservationStatus.PENDING);
        givenReservation(EXPO_ID, roundId, 3L, 1, ReservationStatus.CANCELLED);
        givenReservation(EXPO_ID, roundId, 4L, 1, ReservationStatus.EXPIRED);

        List<AttendeeResponse> attendees = service.attendees(EXPO_ID, null);

        assertThat(attendees).hasSize(3);
        assertThat(attendees).extracting(AttendeeResponse::status)
                .doesNotContain(ReservationStatus.EXPIRED);
    }

    @Test
    @DisplayName("다른 박람회의 예약자가 섞이지 않는다")
    void isolatesByExpo() {
        Long mine = givenRound(EXPO_ID, 100);
        Long theirs = givenRound(OTHER_EXPO_ID, 100);
        givenReservation(EXPO_ID, mine, 1L, 1, ReservationStatus.CONFIRMED);
        givenReservation(OTHER_EXPO_ID, theirs, 2L, 1, ReservationStatus.CONFIRMED);

        assertThat(service.attendees(EXPO_ID, null))
                .allSatisfy(a -> assertThat(a.roundId()).isEqualTo(mine));
    }

    @Test
    @DisplayName("회차가 없거나 예약이 없으면 빈 배열이다")
    void emptyWhenNothingToList() {
        assertThat(service.attendees(999L, null)).isEmpty();
        assertThat(service.attendees(999L, 12345L)).isEmpty();
    }

    @Test
    @DisplayName("연락처는 하이픈 없는 숫자로 나간다 - 받는 쪽이 표시 형식을 입힌다")
    void phoneHasNoHyphen() {
        Long roundId = givenRound(EXPO_ID, 100);
        givenReservation(EXPO_ID, roundId, 1L, 1, ReservationStatus.CONFIRMED);

        assertThat(service.attendees(EXPO_ID, null).get(0).contactPhone())
                .containsOnlyDigits();
    }

    @Test
    @DisplayName("엑셀에 필요한 필드가 모두 채워진다")
    void carriesEveryExcelColumn() {
        Long roundId = givenRound(EXPO_ID, 100);
        givenReservation(EXPO_ID, roundId, 1L, 3, ReservationStatus.CONFIRMED);

        AttendeeResponse attendee = service.attendees(EXPO_ID, null).get(0);

        assertThat(attendee.reservationId()).isNotNull();
        assertThat(attendee.reservationNo()).isNotBlank();
        assertThat(attendee.contactName()).isNotBlank();
        assertThat(attendee.contactPhone()).isNotBlank();
        assertThat(attendee.headcount()).isEqualTo(3);
        assertThat(attendee.amount()).isEqualTo(30000);
        assertThat(attendee.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(attendee.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("toString 에 이름과 연락처가 남지 않는다 - Log 한 줄에 명단 전체가 새면 안 된다")
    void hidesContactInToString() {
        Long roundId = givenRound(EXPO_ID, 100);
        givenReservation(EXPO_ID, roundId, 7L, 1, ReservationStatus.CONFIRMED);

        AttendeeResponse attendee = service.attendees(EXPO_ID, null).get(0);

        assertThat(attendee.toString()).doesNotContain("예약자7");
        assertThat(attendee.toString()).doesNotContain(attendee.contactPhone());
        assertThat(attendee.toString()).contains(attendee.reservationNo(), "CONFIRMED");
    }
}
