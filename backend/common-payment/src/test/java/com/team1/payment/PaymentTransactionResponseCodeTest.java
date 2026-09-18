package com.team1.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * pg_response_code 는 VARCHAR(50) 이다.
 *
 * <p>PortOne 은 성공 응답에 PG 원본 JSON(1,000자 이상)을 함께 준다. 그 값을 그대로 담으면
 * 저장이 터지면서 결제 확정 트랜잭션째로 롤백된다 - 돈은 빠져나갔는데 예약은 미확정으로 남는다.
 * 실제 결제 테스트에서 겪은 문제라 값 길이를 여기서 막는다.
 */
class PaymentTransactionResponseCodeTest {

    private static final Instant NOW = Instant.parse("2026-09-18T01:00:00Z");
    private static final int COLUMN_LENGTH = 50;

    private PaymentTransaction pending() {
        return PaymentTransaction.create(1L, "BE24-01-01TEST", 1000, NOW);
    }

    private String responseCodeOf(PaymentTransaction tx) {
        return tx.getPgResponseCode();
    }

    @Test
    @DisplayName("결제 성공 코드가 컬럼 길이를 넘으면 잘라서 저장한다")
    void clipsLongResponseCodeOnPaid() {
        PaymentTransaction tx = pending();

        tx.markPaid("pgtx-1", "X".repeat(500), NOW);

        assertThat(responseCodeOf(tx)).hasSize(COLUMN_LENGTH);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("결제 실패 코드도 같은 규칙으로 자른다")
    void clipsLongResponseCodeOnFailed() {
        PaymentTransaction tx = pending();

        tx.markFailed("Y".repeat(500), "카드 거절", NOW);

        assertThat(responseCodeOf(tx)).hasSize(COLUMN_LENGTH);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("짧은 코드는 그대로 둔다")
    void keepsShortResponseCode() {
        PaymentTransaction tx = pending();

        tx.markPaid("pgtx-1", "PAID", NOW);

        assertThat(responseCodeOf(tx)).isEqualTo("PAID");
    }

    @Test
    @DisplayName("코드가 없으면 null 그대로 둔다")
    void keepsNullResponseCode() {
        PaymentTransaction tx = pending();

        tx.markPaid("pgtx-1", null, NOW);

        assertThat(responseCodeOf(tx)).isNull();
    }
}
