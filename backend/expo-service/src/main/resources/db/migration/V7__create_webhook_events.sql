CREATE TABLE webhook_events (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    webhook_id   VARCHAR(100) NOT NULL,
    payment_id   BIGINT,
    event_type   VARCHAR(50)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    received_at  DATETIME(6)  NOT NULL,
    processed_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_webhook_events_webhook_id (webhook_id),
    INDEX idx_webhook_events_payment_id (payment_id)
);
