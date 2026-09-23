package com.team1.settlement.controller;

import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import com.team1.settlement.dto.AdminSettlementResponse;
import com.team1.settlement.dto.SettlementPeriod;
import com.team1.settlement.service.SettlementService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSettlementController {

    private final SettlementService settlementService;

    public AdminSettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    /**
     * date 는 그 기간을 잡는 기준일이다 - WEEK 면 그 주, MONTH 면 그 달, YEAR 면 그 해로 넓힌다.
     * summary 는 화면에 실제로 보여줄 조회 1건에서만 true 로 보낸다(전기간 비교·당일 달력용
     * 보조 호출까지 켜면 Gemini 호출이 불필요하게 늘어난다).
     */
    @GetMapping("/settlement")
    public AdminSettlementResponse getSettlement(@RequestParam SettlementPeriod period,
                                                 @RequestParam LocalDate date,
                                                 @RequestParam(defaultValue = "false") boolean summary) {

        requireSuperAdmin();

        return settlementService.getSettlement(period, date, summary);
    }

    private void requireSuperAdmin() {

        AuthenticatedUser user = AuthContext.get();

        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (!"SUPER_ADMIN" .equals(user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
