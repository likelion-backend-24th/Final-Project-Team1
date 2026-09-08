package com.team1.expo.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = {"com.team1.expo", "com.team1.payment"})
@EnableJpaRepositories(basePackages = {"com.team1.expo", "com.team1.payment"})
public class JpaConfig {}
