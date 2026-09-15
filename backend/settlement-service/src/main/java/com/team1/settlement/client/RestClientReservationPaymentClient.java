package com.team1.settlement.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.List;

@Component
public class RestClientReservationPaymentClient implements ReservationPaymentClient {

    private final RestClient restClient;
    private final String internalToken;

    public RestClientReservationPaymentClient(RestClient reservationRestClient,
                                              @Value("${internal.token}")String internalToken){
        this.restClient = reservationRestClient;
        this.internalToken = internalToken;
    }

    @Override
    public List<ReservationPaymentItem> getPayments(Instant from, Instant to) {
        ReservationPaymentItem[] body = restClient.get()
                .uri("/internal/v1/reservations/payments?from={from}&to={to}", from, to)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                .retrieve()
                .body(ReservationPaymentItem[].class);
        return body == null ? List.of() : List.of(body);
    }
}
