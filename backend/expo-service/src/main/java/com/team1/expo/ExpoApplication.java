package com.team1.expo;

import com.team1.payment.PaymentService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = {"com.team1.expo", "com.team1.payment"})
@ComponentScan(
        basePackages = {"com.team1.expo", "com.team1.payment"},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = PaymentService.class)
)
public class ExpoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExpoApplication.class, args);
    }
}