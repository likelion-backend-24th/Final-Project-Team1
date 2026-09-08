package com.team1.reservation.reservation.controller;

import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ApiResponse;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.dto.ConfirmPaymentRequest;
import com.team1.reservation.reservation.dto.ConfirmPaymentResponse;
import com.team1.reservation.reservation.entity.Reservation;
import com.team1.reservation.reservation.service.ReservationPaymentService;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations/{reservationId}/payment")
public class ReservationPaymentController {

    private final ReservationPaymentService reservationPaymentService;

    public ReservationPaymentController(ReservationPaymentService reservationPaymentService) {
        this.reservationPaymentService = reservationPaymentService;
    }


    @PostMapping
    public ApiResponse<ConfirmPaymentResponse> confirm(@PathVariable Long reservationId,
                                                       @Valid @RequestBody ConfirmPaymentRequest request) {
        Reservation reservation =
                reservationPaymentService.confirm(reservationId, currentUser(), request.paymentId());

        return ApiResponse.ok(ConfirmPaymentResponse.from(reservation));
    }

    private AuthenticatedUser currentUser() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "authentication required");
        }
        return user;
    }
}
