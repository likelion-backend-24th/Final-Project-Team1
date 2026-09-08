CREATE TABLE payment_transactions (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    ref_id           BIGINT       NOT NULL,
    amount           INT          NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    pg_transaction_id VARCHAR(100),
    paid_at          DATETIME(6),
    cancelled_at     DATETIME(6),
    failure_reason   VARCHAR(500),
    pg_response_code VARCHAR(50),
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_transactions_promotion FOREIGN KEY (ref_id) REFERENCES expo_promotions (id),
    UNIQUE KEY uk_payment_transactions_ref_id (ref_id),
    INDEX idx_payment_transactions_paid_at (paid_at),
    INDEX idx_payment_transactions_cancelled_at (cancelled_at)
);
