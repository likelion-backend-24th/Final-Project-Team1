package com.team1.reservation.reservation.controller;

import com.team1.reservation.common.ApiResponse;
import com.team1.reservation.reservation.dto.CancelReservationResponse;
import com.team1.reservation.reservation.service.ReservationCancelService;
import com.team1.security.AuthContext;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations/{reservationId}/cancellation")
public class ReservationCancelController {

    private final ReservationCancelService reservationCancelService;

    public ReservationCancelController(ReservationCancelService reservationCancelService) {
        this.reservationCancelService = reservationCancelService;
    }

    // 이미 취소된 예약도 200 이다(멱등). 재시도가 사용자에게 오류로 보이면 안 된다.
    @PatchMapping
    public ApiResponse<CancelReservationResponse> cancel(@PathVariable Long reservationId) {
        return ApiResponse.ok(reservationCancelService.cancel(reservationId, AuthContext.get()));
    }
}
