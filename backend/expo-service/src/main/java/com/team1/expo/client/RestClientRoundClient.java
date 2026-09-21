package com.team1.expo.client;

import com.team1.expo.common.TraceId;
import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.expo.dto.ExpoFeeView;
import com.team1.expo.expo.dto.NearestDeadlineView;
import com.team1.expo.expo.dto.RoundView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public boolean existsByExpo(Long expoId) {
        try {
            ExistsResponse body = restClient.get()
                    .uri("/internal/v1/rounds/exists?expoId={expoId}", expoId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .header(TraceId.HEADER, TraceId.get())
                    .retrieve()
                    .body(ExistsResponse.class);

            if (body == null) {
                throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            }
            return body.exists();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("roundsExists 호출 실패 expoId={} traceId={}", expoId, TraceId.get(), e);
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    @Override
    public List<RoundView> listByExpo(Long expoId) {
        try {
            RoundView[] rounds = restClient.get()
                    .uri("/internal/v1/rounds?expoId={expoId}", expoId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .header(TraceId.HEADER, TraceId.get())
                    .retrieve()
                    .body(RoundView[].class);

            return rounds == null ? List.of() : List.of(rounds);
        } catch (Exception e) {
            log.warn("listRoundsByExpo 호출 실패 expoId={} traceId={}", expoId, TraceId.get(), e);
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    @Override
    public List<ExpoFeeView> feeSummaries(List<Long> expoIds) {
        if (expoIds.isEmpty()) {
            return List.of();
        }
        // expoIds 가 Long 이라 인코딩할 문자가 없다. 반복 파라미터로 붙여 콤마 구분 모호함을 피한다.
        String query = expoIds.stream().map(id -> "expoIds=" + id).collect(Collectors.joining("&"));
        try {
            ExpoFeeView[] summaries = restClient.get()
                    .uri("/internal/v1/rounds/fee-summary?" + query)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .header(TraceId.HEADER, TraceId.get())
                    .retrieve()
                    .body(ExpoFeeView[].class);

            return summaries == null ? List.of() : List.of(summaries);
        } catch (Exception e) {
            log.warn("feeSummaries 호출 실패 count={} traceId={}", expoIds.size(), TraceId.get(), e);
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    @Override
    public List<Long> finishedExpoIds(Instant before, int limit) {
        try {
            Long[] ids = restClient.get()
                    .uri("/internal/v1/rounds/finished-expos?before={before}&limit={limit}", before, limit)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .header(TraceId.HEADER, TraceId.get())
                    .retrieve()
                    .body(Long[].class);
            return ids == null ? List.of() : List.of(ids);
        } catch (Exception e) {
            log.warn("finishedExpoIds 호출 실패 traceId={}", TraceId.get(), e);
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    @Override
    public Map<Long, Instant> nearestDeadlines(List<Long> expoIds) {
        if (expoIds.isEmpty()) {
            return Map.of();
        }
        String query = expoIds.stream().map(id -> "expoIds=" + id).collect(Collectors.joining("&"));
        try {
            NearestDeadlineView[] views = restClient.get()
                    .uri("/internal/v1/rounds/nearest-deadlines?" + query)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .header(TraceId.HEADER, TraceId.get())
                    .retrieve()
                    .body(NearestDeadlineView[].class);
            if (views == null) {
                return Map.of();
            }
            return java.util.Arrays.stream(views)
                    .collect(Collectors.toMap(NearestDeadlineView::expoId, NearestDeadlineView::nearestEndsAt));
        } catch (Exception e) {
            log.warn("nearestDeadlines 호출 실패 count={} traceId={}", expoIds.size(), TraceId.get(), e);
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    private record ExistsResponse(boolean exists) {
    }
}
