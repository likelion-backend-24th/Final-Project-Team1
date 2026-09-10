package com.team1.reservation.client;

import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.common.TraceId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestClientTicketClient implements TicketClient {

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
