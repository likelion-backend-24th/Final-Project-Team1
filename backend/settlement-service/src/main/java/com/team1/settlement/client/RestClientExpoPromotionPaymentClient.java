package com.team1.settlement.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

@Component
public class RestClientExpoPromotionPaymentClient implements ExpoPromotionPaymentClient{

    private final RestClient restClient;
    private final String internalToken;

    public RestClientExpoPromotionPaymentClient(RestClient expoRestClient,
                                              @Value("${internal.token}") String internalToken){
        this.restClient = expoRestClient;
        this.internalToken = internalToken;
    }

    @Override
    public List<ExpoPromotionPaymentItem> getPayments(Instant from, Instant to) {
        ExpoPromotionPaymentItem[] body = restClient.get()
                .uri("/internal/expo-promotions/payments?from={from}&to={to}",from,to)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                .retrieve()
                .body(ExpoPromotionPaymentItem[].class);

        return body == null ? List.of() : List.of(body);
    }
}
