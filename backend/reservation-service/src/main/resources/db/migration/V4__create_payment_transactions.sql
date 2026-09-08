-- ref_id FK는 여기 포함하지 않았다 — 참조 대상 테이블(reservations 등)이 아직 없을 수 있어서다.
-- 참조 대상 테이블이 생기면 별도 마이그레이션에서 다음처럼 추가한다:
--   ALTER TABLE payment_transactions
--     ADD CONSTRAINT fk_payment_transactions_ref_id
--     FOREIGN KEY (ref_id) REFERENCES reservations (id);

CREATE TABLE payment_transactions
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    ref_id            BIGINT       NOT NULL COMMENT '같은 스키마 내 참조(reservations.id 또는 expo_promotions.id) - FK는 이 서비스가 별도로 추가',
    payment_id        VARCHAR(100) NOT NULL COMMENT 'PortOne에 보내는 팀 접두사 포함 ID (BE24-{TEAM}-{ULID})',
    amount            INT          NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    pg_transaction_id VARCHAR(100) NULL COMMENT 'PortOne이 부여하는 거래 ID',
    pg_response_code  VARCHAR(50)  NULL,
    failure_reason    VARCHAR(500) NULL,
    paid_at           DATETIME(6)  NULL COMMENT 'KST',
    cancelled_at      DATETIME(6)  NULL COMMENT 'KST',
    created_at        DATETIME(6)  NOT NULL COMMENT 'KST',
    updated_at        DATETIME(6)  NOT NULL COMMENT 'KST',
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_transactions_ref_id UNIQUE (ref_id),
    CONSTRAINT uk_payment_transactions_payment_id UNIQUE (payment_id),
    CONSTRAINT ck_payment_transactions_status
        CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUND_FAILED', 'CANCELLED')),
    CONSTRAINT ck_payment_transactions_amount CHECK (amount >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 정산 계약(계약2) 기간 필터: (status=PAID AND paid_at) OR (status=CANCELLED AND cancelled_at)
CREATE INDEX idx_payment_transactions_paid_at ON payment_transactions (paid_at);
CREATE INDEX idx_payment_transactions_cancelled_at ON payment_transactions (cancelled_at);
