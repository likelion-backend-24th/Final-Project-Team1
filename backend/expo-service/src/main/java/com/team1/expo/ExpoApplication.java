package com.team1.expo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExpoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExpoApplication.class, args);
    }
}