package com.team1.ticket.client;

import com.team1.ticket.common.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;


// 체크인 시간창(S7-3): Ticket-Service -> 예약-Service, GET /internal/v1/rounds/{roundId}.
// 실패를 삼키고 null 을 돌려준다. 호출부가 시간창 검증만 건너뛴다(fail-open).
@Component
public class RestClientRoundClient implements RoundClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientRoundClient.class);

    private final RestClient restClient;
    private final String internalToken;

    public RestClientRoundClient(RestClient reservationRestClient,
                                 @Value("${internal.token}") String internalToken) {
        this.restClient = reservationRestClient;
        this.internalToken = internalToken;
    }

    @Override
    public RoundInfo findRound(Long roundId) {
        try {
            return restClient.get()
                    .uri("/internal/v1/rounds/{roundId}", roundId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .header(TraceId.HEADER, TraceId.get())
                    .retrieve()
                    .body(RoundInfo.class);

        } catch (Exception e) {
            log.warn("getRoundInternal failed roundId={} traceId={}", roundId, TraceId.get(), e);
            return null;
        }
    }
}
