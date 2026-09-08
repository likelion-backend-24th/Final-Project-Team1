CREATE TABLE webhook_events
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    webhook_id   VARCHAR(255) NOT NULL COMMENT 'PortOne이 부여하는 웹훅 전달 식별자, 중복 수신 방지용',
    payment_id   VARCHAR(100) NOT NULL COMMENT 'payment_transactions.payment_id 논리 참조',
    event_type   VARCHAR(50)  NOT NULL COMMENT '예: Transaction.Paid, Transaction.Cancelled',
    status       VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED' COMMENT 'RECEIVED, PROCESSED, IGNORED',
    received_at  DATETIME(6)  NOT NULL COMMENT 'UTC',
    processed_at DATETIME(6)  NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    CONSTRAINT uk_webhook_events_webhook_id UNIQUE (webhook_id),
    CONSTRAINT ck_webhook_events_status CHECK (status IN ('RECEIVED', 'PROCESSED', 'IGNORED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_webhook_events_payment_id ON webhook_events (payment_id);
