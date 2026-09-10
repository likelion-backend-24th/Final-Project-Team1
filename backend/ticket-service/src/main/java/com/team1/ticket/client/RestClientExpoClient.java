package com.team1.ticket.client;

import com.team1.ticket.common.ApiException;
import com.team1.ticket.common.ErrorCode;
import com.team1.ticket.common.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;


// 체크인 소유권 검증(#7): Ticket-Service → 박람회-Service, GET /internal/v1/expos/{expoId}.
// Timeout·실패는 DEPENDENCY_UNAVAILABLE(503)로 매핑 → 체크인 거부(fail-closed).
@Component
public class RestClientExpoClient implements ExpoClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientExpoClient.class);

    private final RestClient restClient;
    private final String internalToken;

    public RestClientExpoClient(RestClient expoRestClient,
                                @Value("${internal.token}") String internalToken) {
        this.restClient = expoRestClient;
        this.internalToken = internalToken;
    }

    @Override
    public ExpoSummary getExpo(Long expoId) {
        try {
            ExpoSummary summary = restClient.get()
                    .uri("/internal/v1/expos/{expoId}", expoId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .header(TraceId.HEADER, TraceId.get())
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new ApiException(ErrorCode.NOT_FOUND, "expo not found: " + expoId);
                    })
                    .body(ExpoSummary.class);

            if (summary == null || summary.channelOwnerId() == null) {
                throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "expo-service returned an empty body");
            }
            return summary;

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("getExpoInternal failed expoId={} traceId={}", expoId, TraceId.get(), e);
            throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "expo-service unavailable");
        }
    }
}
