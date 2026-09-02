package com.team1.identity.admin.controller;

import com.team1.identity.admin.dto.CreateOrganizerRequest;
import com.team1.identity.admin.dto.CreateOrganizerResponse;
import com.team1.identity.admin.service.AdminService;
import com.team1.identity.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/organizers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateOrganizerResponse> createOrganizer(
            @Valid @RequestBody CreateOrganizerRequest request) {
        return ApiResponse.ok(adminService.createOrganizer(request));
    }
}
