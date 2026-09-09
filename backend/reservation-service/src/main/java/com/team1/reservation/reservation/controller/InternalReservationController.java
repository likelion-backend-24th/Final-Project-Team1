package com.team1.reservation.reservation.controller;

import com.team1.reservation.reservation.dto.AttendeeResponse;
import com.team1.reservation.reservation.dto.ReservationSummaryResponse;
import com.team1.reservation.reservation.service.ReservationQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/reservations")
public class InternalReservationController {

    private final ReservationQueryService reservationQueryService;

    public InternalReservationController(ReservationQueryService reservationQueryService) {
        this.reservationQueryService = reservationQueryService;
    }

    @GetMapping("/summary")
    public List<ReservationSummaryResponse> summary(@RequestParam Long expoId) {
        return reservationQueryService.summary(expoId);
    }


    @GetMapping("/attendees")
    public List<AttendeeResponse> attendees(@RequestParam Long expoId,
                                            @RequestParam(required = false) Long roundId) {
        return reservationQueryService.attendees(expoId, roundId);
    }
}
