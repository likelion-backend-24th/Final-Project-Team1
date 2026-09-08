package com.team1.expo.reservation.controller;

import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.reservation.service.AttendeeExcelService;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/expos")
@RequiredArgsConstructor
public class AttendeeExcelController {

    private final AttendeeExcelService excelService;

    @GetMapping("/{expoId}/reservations/attendees.xlsx")
    public ResponseEntity<byte[]> downloadAttendees(
            @PathVariable Long expoId,
            @RequestParam(required = false) Long roundId) {
        AuthenticatedUser user = requireOrganizer();
        byte[] excel = excelService.generateExcel(user.userId(), expoId, roundId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("attendees.xlsx", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(excel);
    }

    private AuthenticatedUser requireOrganizer() {
        AuthenticatedUser user = AuthContext.get();
        if (user == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (!"ORGANIZER".equals(user.role())) throw new BusinessException(ErrorCode.FORBIDDEN);
        return user;
    }
}
