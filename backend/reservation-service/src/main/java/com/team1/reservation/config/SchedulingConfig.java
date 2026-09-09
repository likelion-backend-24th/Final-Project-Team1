package com.team1.reservation.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @EnableScheduling 을 Application 클래스에 두면 @WebMvcTest Slice 까지 스케줄러를 끌고 온다. */
@Configuration
@ConditionalOnProperty(name = "reservation.expiry.enabled", havingValue = "true")
@EnableScheduling
public class SchedulingConfig {
}
