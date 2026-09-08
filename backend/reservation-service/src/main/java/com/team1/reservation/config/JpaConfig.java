package com.team1.reservation.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 결제 공용 모듈(common-payment)의 엔티티·Repository 까지 스캔 범위를 넓힌다.
 */
@Configuration
@EntityScan(basePackages = {"com.team1.reservation", "com.team1.payment"})
@EnableJpaRepositories(basePackages = {"com.team1.reservation", "com.team1.payment"})
public class JpaConfig {
}
