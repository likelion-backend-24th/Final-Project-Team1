package com.team1.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Component
@Profile("portone-live")
public class PortOneClient implements PgClient {

    private final RestClient restClient;
    private final String storeId;

    public PortOneClient(
            @Value("${portone.api-secret}") String apiSecret,
            @Value("${portone.store-id}") String storeId
    ) {

        this.storeId = storeId;

        ClientHttpRequestFactorySettings settings =
                ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(Duration.ofSeconds(2))
                        .withReadTimeout(Duration.ofSeconds(3));

        ClientHttpRequestFactory requestFactory =
                ClientHttpRequestFactoryBuilder.detect().build(settings);

        this.restClient = RestClient.builder()
                .baseUrl("https://api.portone.io")
                .defaultHeader("Authorization", "PortOne " + apiSecret)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public PgCreateResult create(String paymentId, Integer amount) {

        try {
            restClient.post()
                    .uri("/payments/{paymentId}/pre-register", paymentId)
                    .header("Idempotency-Key", paymentId)
                    .body(new PreRegisterRequest(storeId, amount.longValue()))
                    .retrieve()
                    .toBodilessEntity();
            return new PgCreateResult(true, "0000");
        } catch (RestClientException e) {
            throw new PgCommunicationException("PortOne 사전등록 실패: " + paymentId, e);
        }
    }

    private PgInquiryResult toInquiryResult(PortOnePaymentResponse response) {
        Integer amount = response.amount() != null ? response.amount().total().intValue() : null;
        String channelKey = response.channel() !=null ? response.channel().key() : null;

        return switch (response.status()) {
            case "PAID" -> new PgInquiryResult(
                    PgPaymentStatus.PAID, amount, response.pgTxId(), response.pgResponse(), null, response.storeId(),channelKey);
            case "FAILED" -> {
                String reason = response.failure() != null ? response.failure().reason() : null;
                String pgCode = response.failure() != null ? response.failure().pgCode() : null;
                yield new PgInquiryResult(PgPaymentStatus.FAILED, amount, response.pgTxId(), pgCode, reason,response.storeId(), channelKey);
            }case "CANCELLED" -> new PgInquiryResult(PgPaymentStatus.CANCELLED,amount,response.pgTxId(),null,null,
                    response.storeId(),channelKey);
            default -> new PgInquiryResult(PgPaymentStatus.NOT_FOUND, amount, response.pgTxId(), null, null,response.storeId(), channelKey);
        };
    }

    @Override
    public PgInquiryResult inquire(String paymentId) {
        try {
            // storeId 를 안 넘기면 이 api-secret 의 기본 상점(우리 상점이 아닐 수 있다) 기준으로 찾는다.
            PortOnePaymentResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/payments/{paymentId}")
                            .queryParam("storeId", storeId)
                            .build(paymentId))
                    .retrieve()
                    .body(PortOnePaymentResponse.class);

            if (response == null){
                return  new PgInquiryResult(PgPaymentStatus.NOT_FOUND, null,
                        null,null,null,null,null);
            }

            return toInquiryResult(response);

        } catch (HttpClientErrorException.NotFound e) {
            return new PgInquiryResult(PgPaymentStatus.NOT_FOUND, null,
                    null, null, null,null,null);
        } catch (RestClientException e) {
            throw new PgCommunicationException("PortOne 단건조회 실패: " + paymentId, e);
        }
    }

    @Override
    public PgCancelResult cancel(String paymentId, Integer amount, String reason) {
        try {
            restClient.post()
                    .uri("/payments/{paymentId}/cancel", paymentId)
                    .header("Idempotency-Key", paymentId)
                    .body(new CancelRequest(storeId, amount, reason))
                    .retrieve()
                    .toBodilessEntity();
            return new PgCancelResult(true, "0000");
        } catch (HttpClientErrorException.Conflict e) {
            // 이미 취소된 결제를 재시도한 경우(PAYMENT_ALREADY_CANCELLED, 409). PG 기준으로는
            // 이미 끝난 취소라 성공으로 본다 - 안 그러면 재시도 배치가 끝난 건을 계속 붙잡는다.
            if (e.getResponseBodyAsString().contains("PAYMENT_ALREADY_CANCELLED")) {
                return new PgCancelResult(true, "0000");
            }
            throw new PgCommunicationException("PortOne 취소 실패: " + paymentId, e);
        } catch (RestClientException e) {
            throw new PgCommunicationException("PortOne 취소 실패: " + paymentId, e);
        }
    }

    private record PreRegisterRequest(String storeId, Long totalAmount) {
    }

    private record CancelRequest(String storeId, Integer amount, String reason) {
    }

    private record PaymentAmount(Long total) {
    }

    private record PaymentFailure(String reason, String pgCode) {
    }

    private record SelectedChannel(String key){}

    private record PortOnePaymentResponse(
            String status,
            PaymentAmount amount,
            String pgTxId,
            String pgResponse,
            PaymentFailure failure,
            String storeId,
            SelectedChannel channel
    ) {
    }

}
