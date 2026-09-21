package com.team1.reservation.calendar.controller;

import com.team1.reservation.calendar.dto.CalendarRoundView;
import com.team1.reservation.calendar.service.CalendarSuggestService;
import com.team1.reservation.common.ApiResponse;
import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/me/calendar")
public class CalendarSuggestController {

    private final CalendarSuggestService calendarSuggestService;

    public CalendarSuggestController(CalendarSuggestService calendarSuggestService) {
        this.calendarSuggestService = calendarSuggestService;
    }

    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<List<CalendarRoundView>>> suggest(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String constraint) {

        CalendarSuggestService.Result result =
                calendarSuggestService.suggest(currentUser(), from, to, constraint);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(result.schedule(), result.meta()));
    }

    private AuthenticatedUser currentUser() {
        return AuthContext.get();
    }
}
