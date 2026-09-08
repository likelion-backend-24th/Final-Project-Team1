package com.team1.reservation.reservation.controller;

import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ApiResponse;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.dto.CreateReservationRequest;
import com.team1.reservation.reservation.dto.ReservationResponse;
import com.team1.reservation.reservation.service.ReservationCreation;
import com.team1.reservation.reservation.service.ReservationService;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/rounds/{roundId}/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> create(
            @PathVariable Long roundId,
            @Valid @RequestBody CreateReservationRequest request) {

        ReservationCreation created = reservationService.create(roundId, currentUser(), request);

        return ResponseEntity
                .created(URI.create("/api/v1/reservations/" + created.reservation().getId()))
                .body(ApiResponse.ok(ReservationResponse.from(created)));
    }

    private AuthenticatedUser currentUser() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "authentication required");
        }
        return user;
    }
}
