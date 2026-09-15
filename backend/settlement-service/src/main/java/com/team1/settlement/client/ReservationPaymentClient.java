package com.team1.settlement.client;

import java.time.Instant;
import java.util.List;

public interface ReservationPaymentClient {
    List<ReservationPaymentItem> getPayments(Instant from, Instant to);

}
