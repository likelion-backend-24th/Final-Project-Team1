package com.team1.ticket.client;

import com.team1.ticket.common.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class RestClientReservationSummaryClient implements ReservationSummaryClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientReservationSummaryClient.class);

    private final RestClient restClient;
    private final String internalToken;

    public RestClientReservationSummaryClient(RestClient reservationRestClient,
                                              @Value("${internal.token}") String internalToken) {
        this.restClient = reservationRestClient;
        this.internalToken = internalToken;
    }

    @Override
    public List<ReservationSummary> findSummaries(Long expoId) {
        try {
            List<ReservationSummary> found = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/v1/reservations/summary")
                            .queryParam("expoId", expoId)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .header(TraceId.HEADER, TraceId.get())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ReservationSummary>>() {
                    });
            return found != null ? found : List.of();

        } catch (Exception e) {
            log.warn("reservation summary failed expoId={} traceId={}", expoId, TraceId.get(), e);
            return List.of();
        }
    }
}
