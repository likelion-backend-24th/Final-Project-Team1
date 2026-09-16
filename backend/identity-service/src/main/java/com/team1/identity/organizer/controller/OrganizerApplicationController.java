package com.team1.identity.organizer.controller;

import com.team1.identity.common.response.ApiResponse;
import com.team1.identity.organizer.dto.ApplyOrganizerRequest;
import com.team1.identity.organizer.dto.OrganizerApplicationResponse;
import com.team1.identity.organizer.service.OrganizerApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OrganizerApplication", description = "주최자 신청 — USER Role 필요")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/me/organizer-applications")
@RequiredArgsConstructor
public class OrganizerApplicationController {

    private final OrganizerApplicationService organizerApplicationService;

    @Operation(summary = "주최자 신청", description = "USER가 주최자 신청을 한다. 이미 PENDING 신청이 있으면 409.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrganizerApplicationResponse> apply(@RequestBody ApplyOrganizerRequest request) {
        return ApiResponse.ok(organizerApplicationService.apply(request.reason()));
    }

    @Operation(summary = "내 신청 조회", description = "내 가장 최근 주최자 신청 상태를 조회한다.")
    @GetMapping
    public ApiResponse<OrganizerApplicationResponse> getMyApplication() {
        return ApiResponse.ok(organizerApplicationService.getMyApplication());
    }
}
