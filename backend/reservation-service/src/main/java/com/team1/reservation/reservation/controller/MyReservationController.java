package com.team1.reservation.reservation.controller;

import com.team1.reservation.common.ApiResponse;
import com.team1.reservation.reservation.dto.MyReservationDetailResponse;
import com.team1.reservation.reservation.dto.MyReservationResponse;
import com.team1.reservation.reservation.service.MyReservationService;
import com.team1.security.AuthContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations")
public class MyReservationController {

    private final MyReservationService myReservationService;

    public MyReservationController(MyReservationService myReservationService) {
        this.myReservationService = myReservationService;
    }

    // 본인 예약만 나오므로 목록이 커지지 않는다. 계약대로 페이징을 두지 않는다.
    @GetMapping("/me")
    public ApiResponse<List<MyReservationResponse>> listMine() {
        return ApiResponse.ok(myReservationService.listMine(AuthContext.get()));
    }

    @GetMapping("/{reservationId}")
    public ApiResponse<MyReservationDetailResponse> getMine(@PathVariable Long reservationId) {
        return ApiResponse.ok(myReservationService.getMine(reservationId, AuthContext.get()));
    }
}
