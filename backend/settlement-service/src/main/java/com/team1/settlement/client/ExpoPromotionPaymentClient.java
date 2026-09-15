package com.team1.settlement.client;

import java.time.Instant;
import java.util.List;

public interface ExpoPromotionPaymentClient {
    List<ExpoPromotionPaymentItem> getPayments(Instant from, Instant to);

}
