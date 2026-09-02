package com.team1.reservation.round.controller;

import com.team1.reservation.round.service.RoundService;
import com.team1.reservation.round.dto.ExistsResponse;
import com.team1.reservation.round.dto.InternalRoundResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;


@RestController
@RequestMapping("/internal/v1/rounds")
public class InternalRoundController {

    private final RoundService roundService;

    public InternalRoundController(RoundService roundService) {
        this.roundService = roundService;
    }


    @GetMapping("/exists")
    public ExistsResponse exists(@RequestParam Long expoId) {
        return new ExistsResponse(roundService.existsByExpo(expoId));
    }


    @GetMapping
    public List<InternalRoundResponse> list(@RequestParam Long expoId) {
        return roundService.listByExpo(expoId)
                .stream()
                .map(InternalRoundResponse::from)
                .toList();
    }


    @GetMapping("/finished-expos")
    public List<Long> finishedExpos(@RequestParam Instant before,
                                    @RequestParam(defaultValue = "500") int limit) {
        return roundService.finishedExpoIds(before, limit);
    }
}
