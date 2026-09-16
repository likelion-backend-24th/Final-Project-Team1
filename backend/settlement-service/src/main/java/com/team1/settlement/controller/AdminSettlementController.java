package com.team1.settlement.controller;

import com.team1.security.AuthContext;
import com.team1.security.AuthenticatedUser;
import com.team1.settlement.dto.AdminSettlementResponse;
import com.team1.settlement.service.SettlementService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSettlementController {

    private final SettlementService settlementService;

    public AdminSettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping("/settlement")
    public AdminSettlementResponse getSettlement(@RequestParam int year,
                                                 @RequestParam(required = false) Integer month) {

        requireSuperAdmin();

        return settlementService.getSettlement(year, month);
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
