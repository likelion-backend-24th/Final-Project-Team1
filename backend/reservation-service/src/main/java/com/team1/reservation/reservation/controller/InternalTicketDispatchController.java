package com.team1.reservation.reservation.controller;

import com.team1.reservation.reservation.dto.GaveUpDispatchResponse;
import com.team1.reservation.reservation.entity.TicketDispatchType;
import com.team1.reservation.reservation.service.TicketDispatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


// 포기한 통지 조회(S7-6). ERROR 로그만으로는 아무도 보지 않아 구멍이 열린 줄 모른다.
@RestController
@RequestMapping("/internal/v1/ticket-dispatches")
public class InternalTicketDispatchController {

    private final TicketDispatchService ticketDispatchService;

    public InternalTicketDispatchController(TicketDispatchService ticketDispatchService) {
        this.ticketDispatchService = ticketDispatchService;
    }

    @GetMapping("/gave-up")
    public List<GaveUpDispatchResponse> gaveUp(
            @RequestParam(required = false) TicketDispatchType type,
            @RequestParam(defaultValue = "100") int limit) {

        return ticketDispatchService.gaveUp(type, limit)
                .stream()
                .map(GaveUpDispatchResponse::from)
                .toList();
    }
}
