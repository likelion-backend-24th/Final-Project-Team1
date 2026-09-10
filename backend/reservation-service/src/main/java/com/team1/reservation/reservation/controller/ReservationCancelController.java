package com.team1.reservation.reservation.controller;

import com.team1.reservation.common.ApiResponse;
import com.team1.reservation.reservation.dto.CancelReservationResponse;
import com.team1.reservation.reservation.service.ReservationCancelService;
import com.team1.security.AuthContext;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations/{reservationId}/cancellation")
public class ReservationCancelController {

    private final ReservationCancelService reservationCancelService;

    public ReservationCancelController(ReservationCancelService reservationCancelService) {
        this.reservationCancelService = reservationCancelService;
    }

    @PostMapping
    public ApiResponse<CancelReservationResponse> cancel(@PathVariable Long reservationId) {
        return ApiResponse.ok(reservationCancelService.cancel(reservationId, AuthContext.get()));
    }
}
