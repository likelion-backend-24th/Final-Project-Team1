package com.team1.payment;

import com.github.f4b6a3.ulid.UlidCreator;
import org.springframework.stereotype.Component;


@Component
public class PaymentIdGenerator {

    private static final String TEAM_CODE = "01";

    public String generate() {
        return "BE24-" + TEAM_CODE + "-" + UlidCreator.getUlid();
    }
}
