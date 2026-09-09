package com.team1.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentTransactionRepository repository;

    @Mock
    private PgClient pgClient;

    @Mock
    private PaymentIdGenerator paymentIdGenerator;

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(repository, pgClient, paymentIdGenerator, clock, "store-01", "channel-01");
    }

    private PaymentTransaction pendingTransaction() {
        PaymentTransaction tx = PaymentTransaction.create(1L, "BE24-01-abc", 10000, clock.instant());
        when(repository.findByRefId(1L)).thenReturn(Optional.of(tx));
        return tx;
    }

    @Test
    void confirm_성공하면_SUCCESS_반환하고_PAID로_바뀐다() {
        PaymentTransaction tx = pendingTransaction();
        when(pgClient.inquire("BE24-01-abc"))
                .thenReturn(new PgInquiryResult(PgPaymentStatus.PAID, 10000, "pg-123", "0000", null, "store-01", "channel-01"));

        PaymentApprovalResult result = paymentService.confirm(1L);

        assertThat(result.outcome()).isEqualTo(PaymentApprovalOutcome.SUCCESS);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void confirm_금액이_다르면_AMOUNT_MISMATCH_반환하고_상태는_안바뀐다() {
        PaymentTransaction tx = pendingTransaction();
        when(pgClient.inquire("BE24-01-abc"))
                .thenReturn(new PgInquiryResult(PgPaymentStatus.PAID, 9999, "pg-123", "0000", null, "store-01", "channel-01"));

        PaymentApprovalResult result = paymentService.confirm(1L);

        assertThat(result.outcome()).isEqualTo(PaymentApprovalOutcome.AMOUNT_MISMATCH);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void confirm_store가_다르면_AMOUNT_MISMATCH_반환하고_상태는_안바뀐다() {
        PaymentTransaction tx = pendingTransaction();
        when(pgClient.inquire("BE24-01-abc"))
                .thenReturn(new PgInquiryResult(PgPaymentStatus.PAID, 10000, "pg-123", "0000", null, "store-99", "channel-01"));

        PaymentApprovalResult result = paymentService.confirm(1L);

        assertThat(result.outcome()).isEqualTo(PaymentApprovalOutcome.AMOUNT_MISMATCH);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void confirm_PG가_FAILED면_FAILED_CONFIRMED_반환하고_FAILED로_바뀐다() {
        PaymentTransaction tx = pendingTransaction();
        when(pgClient.inquire("BE24-01-abc"))
                .thenReturn(new PgInquiryResult(PgPaymentStatus.FAILED, null, null, "1001", "카드 한도 초과", null, null));

        PaymentApprovalResult result = paymentService.confirm(1L);

        assertThat(result.outcome()).isEqualTo(PaymentApprovalOutcome.FAILED_CONFIRMED);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void confirm_PG가_NOT_FOUND면_UNKNOWN_반환하고_상태는_안바뀐다() {
        PaymentTransaction tx = pendingTransaction();
        when(pgClient.inquire("BE24-01-abc"))
                .thenReturn(new PgInquiryResult(PgPaymentStatus.NOT_FOUND, null, null, null, null, null, null));

        PaymentApprovalResult result = paymentService.confirm(1L);

        assertThat(result.outcome()).isEqualTo(PaymentApprovalOutcome.UNKNOWN);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void confirm_PG_무응답이면_UNKNOWN_반환하고_상태는_안바뀐다() {
        PaymentTransaction tx = pendingTransaction();
        when(pgClient.inquire("BE24-01-abc"))
                .thenThrow(new PgCommunicationException("timeout"));

        PaymentApprovalResult result = paymentService.confirm(1L);

        assertThat(result.outcome()).isEqualTo(PaymentApprovalOutcome.UNKNOWN);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void cancel_PG취소성고하면_CANCELLED_변환() {
        PaymentTransaction tx = pendingTransaction();
        when(pgClient.cancel("BE24-01-abc", 10000, "고객요청"))
                .thenReturn(new PgCancelResult(true, "0000"));

        paymentService.cancel(1L, "고객요청");
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void cancel_PG취소실패하면_REFUND_FAILED변환() {
        PaymentTransaction tx = pendingTransaction();
        when(pgClient.cancel("BE24-01-abc", 10000, "고객 요청"))
                .thenReturn(new PgCancelResult(false, "9999"));

        paymentService.cancel(1L, "고객 요청");

        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
    }

    @Test
    void cancel_PG_무응답이면_REFUND_FAILED변환() {
        PaymentTransaction tx = pendingTransaction();
        when(pgClient.cancel("BE24-01-abc", 10000, "고객 요청"))
                .thenThrow(new PgCommunicationException("timeout"));

        paymentService.cancel(1L, "고객 요청");

        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
    }

    @Test
    void confirm_PG가_CANCELLED면_FAILED_CONFIRMED_반환하고_CANCELLED로_바뀐다() {
        PaymentTransaction tx = pendingTransaction();
        when(pgClient.inquire("BE24-01-abc"))
                .thenReturn(new PgInquiryResult(PgPaymentStatus.CANCELLED, 10000, "pg-123", "0000", null, "store-01", "channel-01"));

        PaymentApprovalResult result = paymentService.confirm(1L);

        assertThat(result.outcome()).isEqualTo(PaymentApprovalOutcome.FAILED_CONFIRMED);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }
}