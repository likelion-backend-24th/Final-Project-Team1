package com.team1.expo.client;

import com.team1.expo.common.TraceId;
import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class RestClientReservationClient implements ReservationClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientReservationClient.class);

    private final RestClient restClient;
    private final String internalToken;

    public RestClientReservationClient(RestClient reservationRestClient,
                                       @Value("${internal.token}") String internalToken) {
        this.restClient = reservationRestClient;
        this.internalToken = internalToken;
    }

    @Override
    public List<ReservationSummaryItem> getSummary(Long expoId) {
        try {
            ReservationSummaryItem[] body = restClient.get()
                    .uri("/internal/v1/reservations/summary?expoId={expoId}", expoId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .header(TraceId.HEADER, TraceId.get())
                    .retrieve()
                    .body(ReservationSummaryItem[].class);
            return body == null ? List.of() : List.of(body);
        } catch (Exception e) {
            log.warn("getReservationSummary 호출 실패 expoId={}", expoId, e);
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    @Override
    public List<AttendeeItem> getAttendees(Long expoId, Long roundId) {
        try {
            String uri = roundId != null
                    ? "/internal/v1/reservations/attendees?expoId={expoId}&roundId={roundId}"
                    : "/internal/v1/reservations/attendees?expoId={expoId}";
            AttendeeItem[] body = roundId != null
                    ? restClient.get().uri(uri, expoId, roundId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                            .header(TraceId.HEADER, TraceId.get())
                            .retrieve().body(AttendeeItem[].class)
                    : restClient.get().uri(uri, expoId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                            .header(TraceId.HEADER, TraceId.get())
                            .retrieve().body(AttendeeItem[].class);
            return body == null ? List.of() : List.of(body);
        } catch (Exception e) {
            log.warn("getAttendees 호출 실패 expoId={} roundId={}", expoId, roundId, e);
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }
}
