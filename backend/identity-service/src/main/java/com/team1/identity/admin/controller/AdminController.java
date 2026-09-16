package com.team1.identity.admin.controller;

import com.team1.identity.admin.dto.CreateOrganizerRequest;
import com.team1.identity.admin.dto.CreateOrganizerResponse;
import com.team1.identity.admin.service.AdminService;
import com.team1.identity.common.response.ApiResponse;
import com.team1.identity.organizer.dto.OrganizerApplicationResponse;
import com.team1.identity.organizer.dto.RejectApplicationRequest;
import com.team1.identity.organizer.service.OrganizerApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin", description = "전체관리자 전용 — SUPER_ADMIN Role 필요")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final OrganizerApplicationService organizerApplicationService;

    @Operation(
            summary = "주최자 계정 발급",
            description = """
                    전체관리자가 초기 비밀번호를 직접 지정해 주최자 계정을 만든다.
                    권한 검사는 저장보다 먼저 수행하므로, 401·403인 경우 사용자가 생성되지 않는다.
                    사용자와 ORGANIZER Role은 한 Transaction에 저장되며,
                    동시에 같은 이메일로 요청해도 한 건만 생성된다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "INVALID_REQUEST — 형식·정책 위반 (미생성)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "UNAUTHENTICATED — Token 없음·만료·서명 오류 (데이터 불변). WWW-Authenticate: Bearer 부착"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "FORBIDDEN — SUPER_ADMIN이 아닌 주체 (미생성)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "DUPLICATE_EMAIL — 이미 가입된 이메일 (미생성)")
    })
    @PostMapping("/organizers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateOrganizerResponse> createOrganizer(
            @Valid @RequestBody CreateOrganizerRequest request) {
        return ApiResponse.ok(adminService.createOrganizer(request));
    }

    @Operation(summary = "주최자 신청 목록 조회", description = "PENDING 상태의 주최자 신청 목록을 반환한다.")
    @GetMapping("/organizer-applications")
    public ApiResponse<List<OrganizerApplicationResponse>> listApplications() {
        return ApiResponse.ok(organizerApplicationService.listApplications());
    }

    @Operation(summary = "주최자 신청 승인", description = "PENDING 신청을 승인하고 해당 유저에게 ORGANIZER 권한을 부여한다.")
    @PatchMapping("/organizer-applications/{id}/approve")
    public ApiResponse<OrganizerApplicationResponse> approve(@PathVariable Long id) {
        return ApiResponse.ok(organizerApplicationService.approve(id));
    }

    @Operation(summary = "주최자 신청 거절", description = "PENDING 신청을 거절한다. rejectReason은 선택 사항이다.")
    @PatchMapping("/organizer-applications/{id}/reject")
    public ApiResponse<OrganizerApplicationResponse> reject(
            @PathVariable Long id, @RequestBody(required = false) RejectApplicationRequest request) {
        String reason = request != null ? request.rejectReason() : null;
        return ApiResponse.ok(organizerApplicationService.reject(id, reason));
    }
}
