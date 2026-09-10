package com.team1.reservation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableScheduling 을 Application 클래스에 두면 @WebMvcTest Slice 까지 스케줄러를 끌고 온다.
 * 개별 Scheduler Bean 이 각자 프로퍼티로 잠겨 있으므로 여기에는 조건을 걸지 않는다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
