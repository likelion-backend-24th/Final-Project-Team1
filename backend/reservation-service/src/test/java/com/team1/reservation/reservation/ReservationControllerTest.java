package com.team1.reservation.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.controller.ReservationController;
import com.team1.reservation.reservation.dto.CreateReservationRequest;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.service.ReservationCreation;
import com.team1.reservation.reservation.service.ReservationService;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long",
        "internal.token=test-internal-token"
})
class ReservationControllerTest {

    private static final AuthenticatedUser MEMBER = new AuthenticatedUser(100L, "USER");
    private static final Instant NOW = Instant.parse("2026-09-08T04:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        AuthContext.set(MEMBER);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private String body(Integer headcount, String name, String phone) throws Exception {
        return objectMapper.writeValueAsString(new CreateReservationRequest(headcount, name, phone));
    }

    @Test
    @DisplayName("예약 생성은 201 과 Location 헤더를 반환한다")
    void createsReservation() throws Exception {
        Reservation created = Reservation.create("R-4K7Q-W2M8", 7L, 1L, 100L,
                "홍길동", "01012345678", 2, 20000, NOW);
        when(reservationService.create(eq(7L), any(), any()))
                .thenReturn(new ReservationCreation(created, "BE24-01-01JABCDEF"));

        mockMvc.perform(post("/api/v1/rounds/{roundId}/reservations", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(2, "홍길동", "010-1234-5678")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reservationNo").value("R-4K7Q-W2M8"))
                .andExpect(jsonPath("$.data.headcount").value(2))
                .andExpect(jsonPath("$.data.amount").value(20000))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.expiresAt").exists());
    }

    @Test
    @DisplayName("응답 본문에 예약자 이름·연락처를 담지 않는다")
    void responseOmitsContact() throws Exception {
        Reservation created = Reservation.create("R-4K7Q-W2M8", 7L, 1L, 100L,
                "홍길동", "01012345678", 1, 0, NOW);
        when(reservationService.create(eq(7L), any(), any()))
                .thenReturn(new ReservationCreation(created, "BE24-01-01JABCDEF"));

        mockMvc.perform(post("/api/v1/rounds/{roundId}/reservations", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1, "홍길동", "01012345678")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contactName").doesNotExist())
                .andExpect(jsonPath("$.data.contactPhone").doesNotExist());
    }

    @Test
    @DisplayName("정원 초과는 409 CAPACITY_EXCEEDED 로 내려간다")
    void mapsCapacityExceededToConflict() throws Exception {
        when(reservationService.create(eq(7L), any(), any()))
                .thenThrow(new ApiException(ErrorCode.CAPACITY_EXCEEDED, "not enough capacity"));

        mockMvc.perform(post("/api/v1/rounds/{roundId}/reservations", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(5, "홍길동", "01012345678")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("CAPACITY_EXCEEDED"));
    }

    @Test
    @DisplayName("중복 예약은 409 DUPLICATE_RESERVATION 으로 내려간다")
    void mapsDuplicateToConflict() throws Exception {
        when(reservationService.create(eq(7L), any(), any()))
                .thenThrow(new ApiException(ErrorCode.DUPLICATE_RESERVATION, "already reserved"));

        mockMvc.perform(post("/api/v1/rounds/{roundId}/reservations", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1, "홍길동", "01012345678")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("DUPLICATE_RESERVATION"));
    }

    @Test
    @DisplayName("인원이 0 이하면 400 이고 Service 를 호출하지 않는다")
    void rejectsInvalidHeadcount() throws Exception {
        mockMvc.perform(post("/api/v1/rounds/{roundId}/reservations", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(0, "홍길동", "01012345678")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("휴대폰 번호 형식이 아니면 400 이다")
    void rejectsInvalidPhone() throws Exception {
        mockMvc.perform(post("/api/v1/rounds/{roundId}/reservations", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1, "홍길동", "02-123-4567")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("예약자 이름이 비어 있으면 400 이다")
    void rejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/rounds/{roundId}/reservations", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1, "  ", "01012345678")))
                .andExpect(status().isBadRequest());
    }
}
