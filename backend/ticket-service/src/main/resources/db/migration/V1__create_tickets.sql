-- Ticket-Service 소유 스키마: ticket
-- 티켓(Ticket). 예약 확정 시 인원수(headcount)만큼 발급되며, 티켓당 체크인 토큰(QR) 1개를 가진다.
-- QR 1개 = 티켓 1개(= 입장 1건). 예약 1건이 인원 N이면 티켓 N행이 생긴다.

CREATE TABLE tickets
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT      NOT NULL COMMENT '논리 참조 reservation.reservations.id - FK 아님(다른 Service DB)',
    expo_id        BIGINT      NOT NULL COMMENT '논리 참조 expo.expos.id',
    round_id       BIGINT      NOT NULL COMMENT '논리 참조 reservation.rounds.id',
    user_id        BIGINT      NOT NULL COMMENT '논리 참조 identity.users.id - 티켓 소유 회원',
    status         VARCHAR(20) NOT NULL COMMENT 'ISSUED / USED / CANCELLED',
    checkin_token  VARCHAR(64) NOT NULL COMMENT '체크인/QR 토큰(불투명, 서버 조회). 티켓당 1개',
    issued_at      DATETIME(6) NOT NULL COMMENT 'UTC',
    used_at        DATETIME(6) NULL COMMENT 'UTC, 체크인 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tickets_checkin_token (checkin_token),
    INDEX idx_tickets_reservation_id (reservation_id),
    INDEX idx_tickets_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
