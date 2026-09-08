-- Reservation-Service 소유 스키마: reservation
-- 회차(Round). 정원(capacity)은 Sprint 2 에서 예약 상태 변경과 같은 Transaction 안에서 증감한다.

CREATE TABLE rounds
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    expo_id    BIGINT      NOT NULL COMMENT '논리 참조 expo.expos.id - FK 아님(다른 Service DB)',
    starts_at  DATETIME(6) NOT NULL COMMENT 'UTC',
    ends_at    DATETIME(6) NOT NULL COMMENT 'UTC',
    capacity   INT         NOT NULL COMMENT '최대 확정 인원. 1 이상',
    fee        INT         NOT NULL DEFAULT 0 COMMENT '참가비. 0 이면 무료 회차',
    created_at DATETIME(6) NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    CONSTRAINT ck_rounds_capacity CHECK (capacity >= 1),
    CONSTRAINT ck_rounds_period CHECK (ends_at > starts_at),
    CONSTRAINT ck_rounds_fee CHECK (fee >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 상세 조회 시 회차 목록, 소유권 검증
CREATE INDEX idx_rounds_expo_id ON rounds (expo_id);

-- 날짜 필터/정렬, 자동 마감 대상(finishedExpoIds) 조회
CREATE INDEX idx_rounds_ends_at ON rounds (ends_at);
CREATE INDEX idx_rounds_starts_at ON rounds (starts_at);
