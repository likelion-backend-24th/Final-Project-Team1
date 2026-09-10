-- 환불 실패 재시도(#125). REFUND_FAILED 로 남은 건을 배치가 회수할 수 있도록
-- attempts·next_attempt_at 을 payment_transactions 에 직접 추가한다 - 별도 큐 테이블을
-- 두지 않는다. 재시도 대상은 반드시 status = REFUND_FAILED 로만 잡는다.

ALTER TABLE payment_transactions
    ADD COLUMN attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at DATETIME(6) NULL COMMENT 'UTC. REFUND_FAILED 가 아니면 NULL';

ALTER TABLE payment_transactions
    ADD CONSTRAINT ck_payment_transactions_attempts CHECK (attempts >= 0);

-- 배치가 매 주기 훑는 조건이라 복합 Index 로 덮는다.
CREATE INDEX idx_payment_transactions_refund_retry ON payment_transactions (status, next_attempt_at);
