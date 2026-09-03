package com.team1.reservation.config;

import com.team1.security.JwtAuthenticationFilter;
import com.team1.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(
            @Value("${jwt.secret}") String secret) {

        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(new JwtAuthenticationFilter(new JwtValidator(secret)));

        registration.addUrlPatterns("/api/*");
        registration.setOrder(2);   // TraceIdFilter(1) 다음

        return registration;
    }
}
