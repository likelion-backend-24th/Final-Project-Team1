package com.team1.reservation.round.controller;

import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ApiResponse;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.round.service.RoundService;
import com.team1.reservation.round.dto.CreateRoundRequest;
import com.team1.reservation.round.dto.RoundResponse;
import com.team1.reservation.round.dto.UpdateRoundRequest;
import com.team1.reservation.round.entity.Round;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
                .body(ApiResponse.ok(RoundResponse.from(saved, roundService.sequenceOf(saved))));
    }


    @GetMapping
    public ApiResponse<List<RoundResponse>> list(@PathVariable Long expoId) {
        return ApiResponse.ok(RoundResponse.listOf(roundService.listForOrganizer(expoId, currentUser())));
    }


    // 회차 수정. 활성 예약이 0건이고 아직 시작하지 않은 회차만 바꿀 수 있다.
    @PatchMapping("/{roundId}")
    public ApiResponse<RoundResponse> update(@PathVariable Long expoId,
                                             @PathVariable Long roundId,
                                             @Valid @RequestBody UpdateRoundRequest request) {
        Round updated = roundService.update(expoId, roundId, currentUser(), request);
        return ApiResponse.ok(RoundResponse.from(updated, roundService.sequenceOf(updated)));
    }


    // 회차 삭제(소프트). 마지막 살아있는 회차면 박람회가 먼저 비공개로 전환된다.
    @DeleteMapping("/{roundId}")
    public ResponseEntity<Void> delete(@PathVariable Long expoId, @PathVariable Long roundId) {
        roundService.delete(expoId, roundId, currentUser());
        return ResponseEntity.noContent().build();
    }


    private AuthenticatedUser currentUser() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "authentication required");
        }
        return user;
    }
}
