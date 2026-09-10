-- 티켓 발급 통지 재시도 큐(#79).
-- 통지는 fail-open 이라 실패해도 예약은 CONFIRMED 로 남는다. 그러면 티켓만 영영 안 나오므로
-- 통지 대상을 확정과 같은 Transaction 에 기록해 두고, 실패한 건을 배치가 다시 집는다.
--
-- 확정 직후 즉시 한 번 시도하지만, 그 시도 전에 프로세스가 죽어도 이 행이 남아 있어 회수된다.
-- 실패했을 때만 적재하면 커밋과 통지 사이의 죽음은 통째로 유실된다.

CREATE TABLE ticket_dispatch_queue
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    reservation_id  BIGINT       NOT NULL,
    expo_id         BIGINT       NOT NULL,
    round_id        BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    headcount       INT          NOT NULL,

    status          VARCHAR(20)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6)  NOT NULL,
    ticket_id       BIGINT       NULL,
    last_error      VARCHAR(500) NULL,

    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),

    -- 예약당 통지 1건. Ticket-Service 의 tickets.reservation_id UNIQUE 와 짝을 이룬다.
    CONSTRAINT uk_ticket_dispatch_reservation_id UNIQUE (reservation_id),

    -- 배치가 매 주기 훑는 조건이라 복합 Index 로 덮는다.
    KEY idx_ticket_dispatch_due (status, next_attempt_at),

    CONSTRAINT ck_ticket_dispatch_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'GAVE_UP')),
    CONSTRAINT ck_ticket_dispatch_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_ticket_dispatch_headcount CHECK (headcount >= 1)
);
