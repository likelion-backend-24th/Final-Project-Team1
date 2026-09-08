package com.team1.reservation.reservation;

import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.controller.ReservationPaymentController;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.service.ReservationPaymentService;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReservationPaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long",
        "internal.token=test-internal-token"
})
class ReservationPaymentControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-08T04:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationPaymentService reservationPaymentService;

    @BeforeEach
    void setUp() {
        AuthContext.set(new AuthenticatedUser(100L, "USER"));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private Reservation reservation() {
        return Reservation.create("R-4K7Q-W2M8", 7L, 1L, 100L,
                "홍길동", "01012345678", 2, 20000, NOW);
    }

    @Test
    @DisplayName("본문 없이 호출한다 - 결제 식별자는 서버가 예약에 매어 두고 있다")
    void takesNoRequestBody() throws Exception {
        Reservation confirmed = reservation();
        confirmed.confirm(NOW);
        when(reservationPaymentService.confirm(anyLong(), any())).thenReturn(confirmed);

        mockMvc.perform(post("/api/v1/reservations/{id}/payment", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedAt").exists());
    }

    @Test
    @DisplayName("결제 실패확정도 200 이고 본문의 status 로 알린다")
    void returnsCancelledWithOk() throws Exception {
        Reservation cancelled = reservation();
        cancelled.cancel(NOW);
        when(reservationPaymentService.confirm(anyLong(), any())).thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/reservations/{id}/payment", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.confirmedAt").doesNotExist());
    }

    @Test
    @DisplayName("이미 최종 상태면 409 INVALID_STATE_TRANSITION")
    void returnsConflictForTerminalState() throws Exception {
        when(reservationPaymentService.confirm(anyLong(), any()))
                .thenThrow(new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "already CANCELLED"));

        mockMvc.perform(post("/api/v1/reservations/{id}/payment", 42L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("금액 불일치는 400 PAYMENT_AMOUNT_MISMATCH")
    void returnsBadRequestForAmountMismatch() throws Exception {
        when(reservationPaymentService.confirm(anyLong(), any()))
                .thenThrow(new ApiException(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "mismatch"));

        mockMvc.perform(post("/api/v1/reservations/{id}/payment", 42L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("PAYMENT_AMOUNT_MISMATCH"));
    }

    @Test
    @DisplayName("모름은 503 DEPENDENCY_UNAVAILABLE")
    void returnsServiceUnavailableForUnknown() throws Exception {
        when(reservationPaymentService.confirm(anyLong(), any()))
                .thenThrow(new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "unknown"));

        mockMvc.perform(post("/api/v1/reservations/{id}/payment", 42L))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.data.code").value("DEPENDENCY_UNAVAILABLE"));
    }
}
