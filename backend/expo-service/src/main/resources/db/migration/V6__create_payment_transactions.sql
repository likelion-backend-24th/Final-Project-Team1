CREATE TABLE payment_transactions
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    ref_id            BIGINT       NOT NULL COMMENT 'expo_promotions.id 참조 - FK는 별도 마이그레이션',
    payment_id        VARCHAR(100) NOT NULL COMMENT 'PortOne에 보내는 팀 접두사 포함 ID (BE24-D-{ULID})',
    amount            INT          NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    pg_transaction_id VARCHAR(100) NULL COMMENT 'PortOne이 부여하는 거래 ID',
    pg_response_code  VARCHAR(50)  NULL,
    failure_reason    VARCHAR(500) NULL,
    paid_at           DATETIME(6)  NULL COMMENT 'UTC',
    cancelled_at      DATETIME(6)  NULL COMMENT 'UTC',
    created_at        DATETIME(6)  NOT NULL COMMENT 'UTC',
    updated_at        DATETIME(6)  NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_transactions_ref_id UNIQUE (ref_id),
    CONSTRAINT uk_payment_transactions_payment_id UNIQUE (payment_id),
    CONSTRAINT fk_payment_transactions_promotion FOREIGN KEY (ref_id) REFERENCES expo_promotions (id),
    CONSTRAINT ck_payment_transactions_status
        CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUND_FAILED', 'CANCELLED')),
    CONSTRAINT ck_payment_transactions_amount CHECK (amount >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_payment_transactions_paid_at ON payment_transactions (paid_at);
CREATE INDEX idx_payment_transactions_cancelled_at ON payment_transactions (cancelled_at);
