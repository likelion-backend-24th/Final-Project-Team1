package com.team1.reservation.reservation;

import com.team1.reservation.client.ExpoClient;
import com.team1.payment.PaymentService;
import com.team1.payment.PaymentTransaction;
import com.team1.reservation.client.ExpoSummary;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.dto.CreateReservationRequest;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.repository.ReservationRepository;
import com.team1.reservation.reservation.service.ReservationService;
import com.team1.reservation.round.entity.Round;
import com.team1.reservation.round.repository.RoundRepository;
import com.team1.reservation.support.IntegrationTestSupport;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 중복 예약 판정이 상태를 보는지 확인한다. UNIQUE (round_id, user_id) 를 걸었다면
 * 아래 "취소 후 재예약" Test 가 실패한다 - 그게 UNIQUE 를 쓰지 않은 이유다.
 */
class DuplicateReservationTest extends IntegrationTestSupport {

    private static final AuthenticatedUser MEMBER = new AuthenticatedUser(1L, "USER");

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private RoundRepository rounds;

    @Autowired
    private ReservationRepository reservations;

    @MockitoBean
    private ExpoClient expoClient;

    /*
     * 결제 모듈은 실제 PortOne 을 부르므로 Test 에서는 대체한다. 여기서 검증하려는 것은
     * 정원 차감과 중복 판정이지 결제가 아니다.
     */
    @MockitoBean
    private PaymentService paymentService;

    private Long roundId;

    @BeforeEach
    void setUp() {
        reservations.deleteAll();
        rounds.deleteAll();

        Instant now = Instant.now();
        roundId = rounds.save(Round.create(1L, now.plusSeconds(86400), now.plusSeconds(90000), 50, 10000, now))
                .getId();
        when(expoClient.getExpo(anyLong())).thenReturn(new ExpoSummary(1L, 99L, "PUBLISHED"));
        when(paymentService.createPending(any(), any())).thenAnswer(call ->
                PaymentTransaction.create(call.getArgument(0), "BE24-01-01JABCDEF",
                        call.getArgument(1), Instant.now()));
    }

    private CreateReservationRequest request() {
        return new CreateReservationRequest(1, "홍길동", "010-1234-5678");
    }

    @Test
    @DisplayName("같은 회차에 유효한 예약이 있으면 409 DUPLICATE_RESERVATION 으로 거절한다")
    void rejectsSecondActiveReservation() {
        reservationService.create(roundId, MEMBER, request());

        assertThatThrownBy(() -> reservationService.create(roundId, MEMBER, request()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_RESERVATION));

        assertThat(reservations.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("취소한 예약은 재예약을 막지 않는다")
    void allowsRebookingAfterCancel() {
        Reservation first = reservationService.create(roundId, MEMBER, request()).reservation();

        first.cancel(Instant.now());
        reservations.save(first);

        Reservation second = reservationService.create(roundId, MEMBER, request()).reservation();

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(reservations.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("다른 회원은 같은 회차에 각자 예약할 수 있다")
    void allowsDifferentMembersOnSameRound() {
        reservationService.create(roundId, MEMBER, request());
        reservationService.create(roundId, new AuthenticatedUser(2L, "USER"), request());

        assertThat(reservations.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("연락처는 하이픈을 제거해 저장한다")
    void storesPhoneWithoutHyphen() {
        Reservation saved = reservationService.create(roundId, MEMBER, request()).reservation();

        assertThat(saved.getContactPhone()).isEqualTo("01012345678");
    }
}
