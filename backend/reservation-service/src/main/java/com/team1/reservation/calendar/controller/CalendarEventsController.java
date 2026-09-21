package com.team1.reservation.calendar.controller;

import com.team1.reservation.calendar.dto.CalendarRoundView;
import com.team1.reservation.calendar.service.CalendarSuggestService;
import com.team1.reservation.common.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 캘린더 기본 화면(계약: GET /api/v1/calendar/events). 인증 불필요 -
 * 공개 박람회 목록(listExpos)과 같은 성격의 조회라 로그인 없이도 둘러볼 수 있다.
 */
@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarEventsController {

    private final CalendarSuggestService calendarSuggestService;

    public CalendarEventsController(CalendarSuggestService calendarSuggestService) {
        this.calendarSuggestService = calendarSuggestService;
    }

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<CalendarRoundView>>> events(
            @RequestParam Instant from,
            @RequestParam Instant to) {

        List<CalendarRoundView> events = calendarSuggestService.listEvents(from, to);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(events));
    }
}
