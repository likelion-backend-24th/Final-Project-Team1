package com.team1.reservation.client;

import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.common.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    /**
     * 실패를 삼키지 않는다. 비공개에 실패했는데 삭제를 진행하면
     * "회차 0개인 PUBLISHED 박람회" 가 남는다 - #24 가 막으려던 바로 그 상태다.
     */
    @Override
    public void unpublish(Long expoId) {
        try {
            restClient.patch()
                    .uri("/internal/v1/expos/{expoId}/unpublish", expoId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .header(TraceId.HEADER, TraceId.get())
                    .retrieve()
                    .toBodilessEntity();

        } catch (Exception e) {
            log.warn("unpublishExpoInternal failed expoId={} traceId={}", expoId, TraceId.get(), e);
            throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "expo-service unavailable");
        }
    }
}
