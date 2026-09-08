package com.team1.reservation.reservation;

import com.team1.reservation.client.ExpoClient;
import com.team1.reservation.client.ExpoSummary;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.dto.CreateReservationRequest;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.entity.ReservationStatus;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.reservation.service.ReservationNoGenerator;
import com.team1.reservation.reservation.service.ReservationService;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateReservationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T04:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-09-10T02:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-09-10T08:00:00Z");
    private static final Long ROUND_ID = 7L;
    private static final Long EXPO_ID = 1L;
    private static final AuthenticatedUser MEMBER = new AuthenticatedUser(100L, "USER");

    private ReservationRepository reservations;
    private RoundRepository rounds;
    private ExpoClient expoClient;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        reservations = mock(ReservationRepository.class);
        rounds = mock(RoundRepository.class);
        expoClient = mock(ExpoClient.class);
        ReservationNoGenerator generator = () -> "R-4K7Q-W2M8";

        service = new ReservationService(reservations, rounds, expoClient, generator,
                Clock.fixed(NOW, ZoneOffset.UTC));

        givenRound(10000);
        when(expoClient.getExpo(anyLong())).thenReturn(new ExpoSummary(EXPO_ID, 99L, "PUBLISHED"));
        when(reservations.existsByRoundIdAndUserIdAndStatusIn(anyLong(), anyLong(), anyCollection()))
                .thenReturn(false);
        when(rounds.reserve(anyLong(), anyInt())).thenReturn(1);
        when(reservations.save(any(Reservation.class))).thenAnswer(call -> call.getArgument(0));
    }

    private void givenRound(int fee) {
        when(rounds.findById(ROUND_ID))
                .thenReturn(Optional.of(Round.create(EXPO_ID, STARTS, ENDS, 100, fee, NOW)));
    }

    private CreateReservationRequest request(int headcount) {
        return new CreateReservationRequest(headcount, "  홍길동 ", "010-1234-5678");
    }

    @Test
    @DisplayName("예약을 만들면 PENDING 이고 금액은 인원 x 참가비다")
    void createsPendingReservation() {
        Reservation created = service.create(ROUND_ID, MEMBER, request(3));

        assertThat(created.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(created.getAmount()).isEqualTo(30000);
        assertThat(created.getHeadcount()).isEqualTo(3);
        assertThat(created.getRoundId()).isEqualTo(ROUND_ID);
        assertThat(created.getExpoId()).isEqualTo(EXPO_ID);
        assertThat(created.getUserId()).isEqualTo(MEMBER.userId());
        assertThat(created.getExpiresAt()).isEqualTo(NOW.plus(Reservation.PAYMENT_WINDOW));
    }

    @Test
    @DisplayName("연락처는 하이픈을 제거하고 이름은 앞뒤 공백을 지워 저장한다")
    void normalizesContact() {
        Reservation created = service.create(ROUND_ID, MEMBER, request(1));

        assertThat(created.getContactPhone()).isEqualTo("01012345678");
        assertThat(created.getContactName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("무료 회차는 금액이 0이다")
    void freeRoundHasZeroAmount() {
        givenRound(0);

        assertThat(service.create(ROUND_ID, MEMBER, request(4)).getAmount()).isZero();
    }

    @Test
    @DisplayName("정원이 부족하면 409 CAPACITY_EXCEEDED 이고 예약 행을 만들지 않는다")
    void rejectsWhenCapacityExceeded() {
        when(rounds.reserve(anyLong(), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> service.create(ROUND_ID, MEMBER, request(5)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CAPACITY_EXCEEDED));

        verify(reservations, never()).save(any());
    }

    @Test
    @DisplayName("중복 예약은 정원을 차감하기 전에 거절한다")
    void rejectsDuplicateBeforeReserving() {
        when(reservations.existsByRoundIdAndUserIdAndStatusIn(anyLong(), anyLong(), anyCollection()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(ROUND_ID, MEMBER, request(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_RESERVATION));

        verify(rounds, never()).reserve(anyLong(), anyInt());
        verify(reservations, never()).save(any());
    }

    @Test
    @DisplayName("중복 검사는 PENDING·CONFIRMED 만 대상으로 한다 - 취소·만료는 재예약을 막지 않는다")
    void duplicateCheckOnlyLooksAtActiveStatuses() {
        ArgumentCaptor<Collection<ReservationStatus>> captor = ArgumentCaptor.forClass(Collection.class);

        service.create(ROUND_ID, MEMBER, request(1));

        verify(reservations).existsByRoundIdAndUserIdAndStatusIn(
                eq(ROUND_ID), eq(MEMBER.userId()), captor.capture());

        assertThat(captor.getValue())
                .containsExactlyInAnyOrder(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("없는 회차는 404")
    void rejectsUnknownRound() {
        when(rounds.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(999L, MEMBER, request(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("이미 종료된 회차는 404 - 존재 여부를 흘리지 않는다")
    void rejectsFinishedRound() {
        Instant afterEnd = ENDS.plusSeconds(1);
        ReservationService late = new ReservationService(reservations, rounds, expoClient,
                () -> "R-4K7Q-W2M8", Clock.fixed(afterEnd, ZoneOffset.UTC));

        assertThatThrownBy(() -> late.create(ROUND_ID, MEMBER, request(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(rounds, never()).reserve(anyLong(), anyInt());
    }

    @Test
    @DisplayName("마감된 박람회의 회차는 404")
    void rejectsClosedExpo() {
        when(expoClient.getExpo(anyLong())).thenReturn(new ExpoSummary(EXPO_ID, 99L, "CLOSED"));

        assertThatThrownBy(() -> service.create(ROUND_ID, MEMBER, request(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("박람회 상태를 확인하지 못하면 예약을 만들지 않는다 - 쓰기는 fail-closed 다")
    void failsClosedWhenExpoServiceUnavailable() {
        when(expoClient.getExpo(anyLong()))
                .thenThrow(new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "expo-service unavailable"));

        assertThatThrownBy(() -> service.create(ROUND_ID, MEMBER, request(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));

        verify(rounds, never()).reserve(anyLong(), anyInt());
        verify(reservations, never()).save(any());
    }

    @Test
    @DisplayName("USER 가 아닌 Role 은 403, 미인증은 401")
    void rejectsNonMember() {
        assertThatThrownBy(() -> service.create(ROUND_ID, new AuthenticatedUser(1L, "ORGANIZER"), request(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.create(ROUND_ID, null, request(1)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }
}
