package com.team1.reservation.round.controller;

import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ApiResponse;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.round.service.RoundService;
import com.team1.reservation.round.dto.CreateRoundRequest;
import com.team1.reservation.round.dto.RoundResponse;
import com.team1.reservation.round.entity.Round;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/expos/{expoId}/rounds")
public class RoundController {

    private final RoundService roundService;

    public RoundController(RoundService roundService) {
        this.roundService = roundService;
    }


    @PostMapping
    public ResponseEntity<ApiResponse<RoundResponse>> create(@PathVariable Long expoId,
                                                             @Valid @RequestBody CreateRoundRequest request) {
        Round saved = roundService.create(expoId, currentUser(), request);
        return ResponseEntity
                .created(URI.create("/api/v1/expos/" + expoId + "/rounds/" + saved.getId()))
                .body(ApiResponse.ok(RoundResponse.from(saved)));
    }


    @GetMapping
    public ApiResponse<List<RoundResponse>> list(@PathVariable Long expoId) {
        List<RoundResponse> body = roundService.listForOrganizer(expoId, currentUser())
                .stream()
                .map(RoundResponse::from)
                .toList();
        return ApiResponse.ok(body);
    }


    private AuthenticatedUser currentUser() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "authentication required");
        }
        return user;
    }
}
