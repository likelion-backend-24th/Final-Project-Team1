-- 템플릿 SQL — 이 파일은 실행되지 않는다.
-- 사용 방법: payment_transactions.sql과 같은 방식 — 이 서비스를 참조하는 각 서비스가
-- 자기 Flyway 마이그레이션 폴더에 다음 V번호로 복사한다.
-- reservation-service는 V4(payment_transactions) 다음이므로 V5__create_webhook_events.sql
--
-- payment_id는 같은 스키마의 payment_transactions.payment_id를 참조하지만, 웹훅이 대상 결제보다
-- 먼저 도착하거나(드묾) payment_transactions 행이 아직 없는 극단적 케이스를 배제하지 않기 위해
-- FK를 걸지 않는다 — 조회 실패는 애플리케이션이 처리한다.

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

-- 같은 결제의 웹훅 이력을 시간순으로 조회할 때 사용
CREATE INDEX idx_webhook_events_payment_id ON webhook_events (payment_id);
