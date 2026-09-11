package com.team1.reservation.client;

import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.common.TraceId;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestClientTicketClient implements TicketClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientTicketClient.class);

    private final RestClient restClient;
    private final String internalToken;

    public RestClientTicketClient(RestClient ticketRestClient,
                                  @Value("${internal.token}") String internalToken) {
        this.restClient = ticketRestClient;
        this.internalToken = internalToken;
    }

    @Override
    public IssuedTicket issueTicket(IssueTicketCommand command) {
        try {
            IssuedTicket issued = restClient.post()
                    .uri("/internal/v1/tickets")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .header(TraceId.HEADER, TraceId.get())
                    .body(command)
                    .retrieve()
                    .body(IssuedTicket.class);

            if (issued == null || issued.ticketId() == null) {
                throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "ticket-service returned an empty body");
            }
            return issued;

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "ticket-service unavailable");
        }
    }

    /**
     * 조회 실패를 예외로 올리지 않는다. 예약 조회는 부분 실패를 허용하는 경로이고,
     * 티켓이 아직 없는 것(404)은 애초에 오류가 아니라 정상 상태다.
     */
    @Override
    public TicketDetail findTicket(Long reservationId) {
        try {
            return getTicket(reservationId);
        } catch (Exception e) {
            log.warn("findTicket failed reservationId={} traceId={}", reservationId, TraceId.get(), e);
            return null;
        }
    }

    /** 같은 조회지만 실패를 삼키지 않는다. 취소 경로가 쓴다. */
    @Override
    public TicketDetail findTicketFailClosed(Long reservationId) {
        try {
            return getTicket(reservationId);
        } catch (Exception e) {
            log.warn("ticket lookup failed reservationId={} traceId={}", reservationId, TraceId.get(), e);
            throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "ticket-service unavailable");
        }
    }

    private TicketDetail getTicket(Long reservationId) {
        return restClient.get()
                .uri("/internal/v1/tickets/reservation/{reservationId}", reservationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                .header(TraceId.HEADER, TraceId.get())
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, response) -> {
                    // 통지가 아직 안 나갔거나 재시도 큐에 걸려 있다.
                })
                .body(TicketDetail.class);
    }

    @Override
    public void revokeTicket(Long reservationId) {
        try {
            restClient.patch()
                    .uri("/internal/v1/tickets/reservation/{reservationId}/revoke", reservationId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .header(TraceId.HEADER, TraceId.get())
                    .retrieve()
                    .toBodilessEntity();

        } catch (Exception e) {
            throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "ticket-service unavailable");
        }
    }
}
