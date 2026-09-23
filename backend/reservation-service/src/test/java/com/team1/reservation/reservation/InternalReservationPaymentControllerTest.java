package com.team1.reservation.reservation;

import com.team1.reservation.reservation.controller.InternalReservationController;
import com.team1.reservation.reservation.dto.InternalReservationPaymentResponse;
import com.team1.reservation.reservation.service.ReservationQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long",
        "internal.token=test-internal-token"
})
class InternalReservationPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationQueryService reservationQueryService;

    @Test
    @DisplayName("정산 결제 목록을 조회한다")
    void 정산_결제_조회() throws Exception {
        InternalReservationPaymentResponse tx = new InternalReservationPaymentResponse(
                "PAY-1", 30000, "PAID",
                Instant.parse("2026-09-10T09:30:00Z"), null,
                Instant.parse("2026-09-10T09:30:00Z"), 42L, 7L);

        when(reservationQueryService.getPaymentsForSettlement(any(), any()))
                .thenReturn(List.of(tx));

        mockMvc.perform(get("/internal/v1/reservations/payments")
                        .param("from", "2026-09-01T00:00:00Z")
                        .param("to", "2026-09-30T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentId").value("PAY-1"))
                .andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[0].reservationId").value(42))
                .andExpect(jsonPath("$[0].expoId").value(7));
    }
}
