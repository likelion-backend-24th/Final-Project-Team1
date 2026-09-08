CREATE TABLE expo_promotions (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    expo_id      BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL,
    amount       INT         NOT NULL,
    paid_at      DATETIME(6),
    cancelled_at DATETIME(6),
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_expo_promotions_expo FOREIGN KEY (expo_id) REFERENCES expos (id),
    INDEX idx_expo_promotions_status_expo (status, expo_id)
);
