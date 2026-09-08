package com.team1.reservation.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 금액을 Client 에게서 받지 않는다. 받아서 대조하면 위조된 금액과 대조하는 셈이라
 * 의미가 없다. 금액은 PG 조회 응답과 DB 의 예약 금액을 대조한다.
 */
public record ConfirmPaymentRequest(

        @NotBlank @Size(max = 100) String paymentId) {
}
