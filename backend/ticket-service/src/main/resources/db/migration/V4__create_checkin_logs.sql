-- 체크인 이력(S7-5). 지금은 tickets.used_at 하나뿐이라 "누가" 체크인했는지 남지 않는다.
-- 현장에 스태프가 여럿이면 분쟁이 났을 때 추적할 수 없다.
--
-- tickets 를 갱신하지 않고 행을 쌓는 이유: 되돌리기(S7-4)로 USED -> ISSUED 가 오가므로
-- 티켓 한 행에는 "마지막 상태" 만 남는다. 누가 언제 무엇을 했는지는 append-only 로만 보존된다.
--
-- FK 를 걸지 않는다 - 티켓과 같은 DB 지만, 이력은 티켓이 지워져도 남아야 하는 성격이다.

CREATE TABLE checkin_logs
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    ticket_id     BIGINT      NOT NULL COMMENT '논리 참조 ticket.tickets.id',
    action        VARCHAR(20) NOT NULL COMMENT 'CHECK_IN / CANCEL',
    actor_user_id BIGINT      NOT NULL COMMENT '논리 참조 identity.users.id - 처리한 주최자',
    method        VARCHAR(20) NULL COMMENT 'QR / RESERVATION_NO - 화면이 안 보내면 NULL',
    created_at    DATETIME(6) NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    INDEX idx_checkin_logs_ticket_id (ticket_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
