package com.team1.settlement.client;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class SettlementClientConfig {

    @Bean
    public RestClient reservationRestClient(
            @Value("${reservation-service.base-url}") String baseUrl,
            @Value("${reservation-service.connect-timeout}")Duration connectTimeout,
            @Value("${reservation-service.read-timeout}") Duration readTimeout){

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Bean
    public RestClient expoRestClient(
            @Value("${expo-service.base-url}") String baseUrl,
            @Value("${expo-service.connect-timeout}") Duration connectTimeout,
            @Value("${expo-service.read-timeout}") Duration readTimeout
    ){
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
